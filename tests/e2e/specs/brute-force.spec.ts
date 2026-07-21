import { test, expect } from '@playwright/test';
import { LoginPage } from '../helpers/login.js';
import { OtpForm } from '../helpers/otp-form.js';
import { mailpit } from '../setup/mailpit.js';
import { keycloakAdmin } from '../setup/keycloak-admin.js';
import { TEST_PASSWORD, BRUTE_FORCE_FAILURE_FACTOR } from '../setup/global-setup.js';

const REALM = 'test-brute-force';
const WRONG_CODE = 'WRONG1';

// The brute-force protector processes failures on a background worker, so
// the attack-detection counters can lag the HTTP response briefly. These
// accessors are meant to be polled via expect.poll.
function bruteForceStatusAccessors(userId: string) {
  return {
    numFailures: async () =>
      (await keycloakAdmin.getBruteForceStatus(REALM, userId)).numFailures,
    disabled: async () =>
      (await keycloakAdmin.getBruteForceStatus(REALM, userId)).disabled,
  };
}

test.describe('Brute-Force Protection', () => {
  test.beforeEach(async () => {
    // Clear emails before each test
    await mailpit.deleteAllMessages();
  });

  test('each failed OTP submission increments the brute-force failure counter', async ({ page }) => {
    const loginPage = new LoginPage(page);
    const otpForm = new OtpForm(page);

    const user = await keycloakAdmin.getUserByUsername(REALM, 'counter-user');
    expect(user).not.toBeNull();
    const status = bruteForceStatusAccessors(user!.id);

    // Start from a clean slate so retries and repeated runs are deterministic
    await keycloakAdmin.clearBruteForceStatus(REALM, user!.id);
    await expect.poll(status.numFailures).toBe(0);
    await expect.poll(status.disabled).toBe(false);

    await loginPage.goto(REALM);
    await loginPage.login('counter-user', TEST_PASSWORD);
    await otpForm.expectVisible();

    const email = await mailpit.waitForMessage('counter-user@test.local');
    const code = mailpit.extractOtpCode(email);
    expect(code).not.toBeNull();

    // Stay below the lockout threshold so the account remains usable
    for (let attempt = 1; attempt < BRUTE_FORCE_FAILURE_FACTOR; attempt++) {
      await otpForm.enterCode(WRONG_CODE);
      await otpForm.expectVisible();
      await otpForm.expectError();

      await expect.poll(status.numFailures).toBe(attempt);
    }

    // Below the threshold the correct code still logs in
    await otpForm.enterCode(code!);
    await loginPage.expectLoggedIn();
  });

  test('reaching the failure threshold locks the account and rejects even the correct code', async ({ page }) => {
    const loginPage = new LoginPage(page);
    const otpForm = new OtpForm(page);

    const user = await keycloakAdmin.getUserByUsername(REALM, 'lockout-user');
    expect(user).not.toBeNull();
    const status = bruteForceStatusAccessors(user!.id);

    // Start from a clean slate so retries and repeated runs are deterministic
    await keycloakAdmin.clearBruteForceStatus(REALM, user!.id);
    await expect.poll(status.numFailures).toBe(0);
    await expect.poll(status.disabled).toBe(false);

    await loginPage.goto(REALM);
    await loginPage.login('lockout-user', TEST_PASSWORD);
    await otpForm.expectVisible();

    const email = await mailpit.waitForMessage('lockout-user@test.local');
    const code = mailpit.extractOtpCode(email);
    expect(code).not.toBeNull();

    for (let attempt = 1; attempt <= BRUTE_FORCE_FAILURE_FACTOR; attempt++) {
      await otpForm.enterCode(WRONG_CODE);
      await otpForm.expectVisible();
      await otpForm.expectError();
    }

    // The threshold is reached: the account is temporarily locked
    await expect.poll(status.numFailures).toBeGreaterThanOrEqual(BRUTE_FORCE_FAILURE_FACTOR);
    await expect.poll(status.disabled).toBe(true);

    // Even the correct code is refused while the account is locked
    await otpForm.enterCode(code!);
    await otpForm.expectVisible();
    await otpForm.expectError();
    await expect.poll(status.disabled).toBe(true);
  });
});
