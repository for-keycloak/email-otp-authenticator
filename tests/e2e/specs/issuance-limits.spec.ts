import { test, expect, Page } from '@playwright/test';
import { LoginPage } from '../helpers/login.js';
import { OtpForm } from '../helpers/otp-form.js';
import { mailpit } from '../setup/mailpit.js';
import { keycloakAdmin } from '../setup/keycloak-admin.js';
import {
  TEST_PASSWORD,
  BRUTE_FORCE_FAILURE_FACTOR,
  RESEND_COOLDOWN_SECONDS,
  ISSUANCE_LIMIT,
} from '../setup/global-setup.js';

const REALM = 'test-issuance-limits';

// A refused request sends nothing, so there is no email to wait for. Give a
// wrongly sent email time to reach Mailpit before checking for it.
const NO_EMAIL_GRACE_MS = 2000;

const COOLDOWN_MESSAGE = /Please wait \d+ seconds? before requesting a new code/;
const LIMIT_MESSAGE = 'Too many codes have been requested. Please try again later.';

// The issuance count is per user and outlives the test (it expires with its
// window), so every test, and every retry, gets a fresh user.
async function createFreshUser(prefix: string): Promise<{ id: string; username: string; email: string }> {
  const username = `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
  const email = `${username}@test.local`;
  const id = await keycloakAdmin.createUser(REALM, username, email, TEST_PASSWORD);
  return { id, username, email };
}

async function startLogin(page: Page, username: string): Promise<OtpForm> {
  const loginPage = new LoginPage(page);
  const otpForm = new OtpForm(page);

  await loginPage.goto(REALM);
  await loginPage.login(username, TEST_PASSWORD);
  await otpForm.expectVisible();

  return otpForm;
}

async function expectNoNewEmail(email: string, seen: Set<string>) {
  await new Promise((resolve) => setTimeout(resolve, NO_EMAIL_GRACE_MS));
  expect(await mailpit.getUnseenMessagesTo(email, seen)).toHaveLength(0);
}

// The brute-force protector counts failures on a background worker, so callers
// read this only after NO_EMAIL_GRACE_MS has passed since the last request.
async function expectNotLockedOut(userId: string) {
  const status = await keycloakAdmin.getBruteForceStatus(REALM, userId);
  expect(status.numFailures).toBe(0);
  expect(status.disabled).toBe(false);
}

test.describe('Issuance Limits', () => {
  // The tests wait on real time (the cooldown) and inspect the shared inbox
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(async () => {
    await mailpit.deleteAllMessages();
  });

  test('resend within the cooldown sends no second email and the current code still works', async ({ page }) => {
    const user = await createFreshUser('cooldown-user');
    const seen = new Set<string>();

    const otpForm = await startLogin(page, user.username);
    const code = mailpit.extractOtpCode(await mailpit.waitForNewMessage(user.email, seen));
    expect(code).not.toBeNull();

    // More refused resends than the brute-force failure factor: none may count as a failure
    for (let attempt = 0; attempt <= BRUTE_FORCE_FAILURE_FACTOR; attempt++) {
      await otpForm.clickResend();
      await otpForm.expectVisible();
      await expect(page.getByText(COOLDOWN_MESSAGE)).toBeVisible();
    }

    await expectNoNewEmail(user.email, seen);
    await expectNotLockedOut(user.id);

    // The code from the first email is still valid
    await otpForm.enterCode(code!);
    await new LoginPage(page).expectLoggedIn();
  });

  test('after the cooldown, resend emails a new, different code', async ({ page }) => {
    test.setTimeout(90_000);
    const user = await createFreshUser('cooldown-pass-user');
    const seen = new Set<string>();

    const otpForm = await startLogin(page, user.username);
    const firstCode = mailpit.extractOtpCode(await mailpit.waitForNewMessage(user.email, seen));
    expect(firstCode).not.toBeNull();

    await page.waitForTimeout((RESEND_COOLDOWN_SECONDS + 1) * 1000);
    await otpForm.clickResend();
    await otpForm.expectVisible();
    await expect(page.getByText(COOLDOWN_MESSAGE)).not.toBeVisible();

    const secondCode = mailpit.extractOtpCode(await mailpit.waitForNewMessage(user.email, seen));
    expect(secondCode).not.toBeNull();
    expect(secondCode).not.toBe(firstCode);

    await otpForm.enterCode(secondCode!);
    await new LoginPage(page).expectLoggedIn();
  });

  test('repeated fresh logins stop receiving email once the limit is reached, without locking the account', async ({ page, browser }) => {
    test.setTimeout(120_000);
    const user = await createFreshUser('limit-user');
    const seen = new Set<string>();

    // Each login from a clean browser state is a new authentication session and emails a new code
    let otpForm: OtpForm | undefined;
    let latestCode: string | null = null;
    for (let login = 1; login <= ISSUANCE_LIMIT; login++) {
      await page.context().clearCookies();
      otpForm = await startLogin(page, user.username);
      latestCode = mailpit.extractOtpCode(await mailpit.waitForNewMessage(user.email, seen));
    }
    expect(latestCode).not.toBeNull();

    // One more fresh login, from another browser, is refused: no email, a generic message.
    // Submitting a code there is not a failed attempt, since no code was sent to guess.
    const otherContext = await browser.newContext();
    try {
      const otherPage = await otherContext.newPage();
      const otherOtpForm = await startLogin(otherPage, user.username);
      await expect(otherPage.getByText(LIMIT_MESSAGE)).toBeVisible();

      await otherOtpForm.enterCode('WRONG1');
      await otherOtpForm.expectVisible();
      await expect(otherPage.getByText(LIMIT_MESSAGE)).toBeVisible();
    } finally {
      await otherContext.close();
    }

    // A resend in the latest session is refused too, once past the cooldown
    await page.waitForTimeout((RESEND_COOLDOWN_SECONDS + 1) * 1000);
    await otpForm!.clickResend();
    await otpForm!.expectVisible();
    await expect(page.getByText(LIMIT_MESSAGE)).toBeVisible();

    await expectNoNewEmail(user.email, seen);
    await expectNotLockedOut(user.id);

    // The latest code is still valid
    await otpForm!.enterCode(latestCode!);
    await new LoginPage(page).expectLoggedIn();
  });
});
