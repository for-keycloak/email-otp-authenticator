import { test, expect } from '@playwright/test';
import { LoginPage } from '../helpers/login.js';
import { OtpForm, TotpForm, TwoFactorChoice } from '../helpers/otp-form.js';
import { mailpit } from '../setup/mailpit.js';
import { TEST_PASSWORD } from '../setup/global-setup.js';
import { getTotpSecret } from '../setup/totp-setup.js';
import * as OTPAuth from 'otpauth';

const REALM = 'test-otp-alternative';

function generateTotpCode(username: string): string {
  const secret = getTotpSecret(username);
  if (!secret) {
    throw new Error(`No TOTP secret found for user ${username}`);
  }

  const totp = new OTPAuth.TOTP({
    issuer: 'Keycloak',
    label: username,
    algorithm: 'SHA1',
    digits: 6,
    period: 30,
    secret: secret,
  });

  return totp.generate();
}

test.describe('Email OTP Alternative Flow', () => {
  test.beforeEach(async () => {
    await mailpit.deleteAllMessages();
  });

  test.describe('Role Filtering in Alternative Mode', () => {
    test('user without role, no TOTP - skips 2FA entirely', async ({
      page,
    }) => {
      const loginPage = new LoginPage(page);

      await loginPage.goto(REALM);
      await loginPage.login('user-without-role', TEST_PASSWORD);

      // Should be logged in directly
      await loginPage.expectLoggedIn();

      // No email should be sent
      const messages = await mailpit.getMessages();
      expect(messages.total).toBe(0);
    });

    test('user without role, has TOTP - sees TOTP only', async ({ page }) => {
      const loginPage = new LoginPage(page);
      const totpForm = new TotpForm(page);

      await loginPage.goto(REALM);
      await loginPage.login('user-totp-only', TEST_PASSWORD);

      // Should see TOTP form only (no email OTP option)
      await totpForm.expectVisible();

      // No email should be sent
      const messages = await mailpit.getMessages();
      expect(messages.total).toBe(0);

      // Complete TOTP login
      const totpCode = generateTotpCode('user-totp-only');
      await totpForm.enterCode(totpCode);

      await loginPage.expectLoggedIn();
    });

    test('user with role, no TOTP - sees Email OTP', async ({ page }) => {
      const loginPage = new LoginPage(page);
      const otpForm = new OtpForm(page);

      await loginPage.goto(REALM);
      await loginPage.login('user-with-role', TEST_PASSWORD);

      // Should see Email OTP form
      await otpForm.expectVisible();

      // Email should be sent
      const email = await mailpit.waitForMessage('user-with-role@test.local');
      const code = mailpit.extractOtpCode(email);
      expect(code).not.toBeNull();

      // Complete login
      await otpForm.enterCode(code!);
      await loginPage.expectLoggedIn();
    });

    test('user with role, has TOTP - sees both options, choose Email OTP', async ({
      page,
    }) => {
      const loginPage = new LoginPage(page);
      const otpForm = new OtpForm(page);
      const choice = new TwoFactorChoice(page);

      await loginPage.goto(REALM);
      await loginPage.login('user-both-options', TEST_PASSWORD);

      // Check if we're already on email OTP form
      const isOnEmailOtp = await page.locator('#email-otp').isVisible({ timeout: 2000 }).catch(() => false);

      if (!isOnEmailOtp) {
        // We're on TOTP form - switch to email OTP
        await choice.chooseEmailOtp();
      }

      // Now we should be on Email OTP form
      await otpForm.expectVisible();

      // Get code and complete login
      const email = await mailpit.waitForMessage(
        'user-both-options@test.local'
      );
      const code = mailpit.extractOtpCode(email);
      expect(code).not.toBeNull();

      await otpForm.enterCode(code!);
      await loginPage.expectLoggedIn();
    });

    test('user with role, has TOTP - sees both options, choose TOTP', async ({
      page,
    }) => {
      const loginPage = new LoginPage(page);
      const totpForm = new TotpForm(page);
      const choice = new TwoFactorChoice(page);

      await loginPage.goto(REALM);
      await loginPage.login('user-both-options', TEST_PASSWORD);

      // With Email OTP first in the flow, user will see Email OTP form first
      // Check if we're on TOTP form already
      const isOnTotpForm = await page.locator('#otp').isVisible({ timeout: 2000 }).catch(() => false);

      if (!isOnTotpForm) {
        // We're on Email OTP form - try to switch to TOTP
        await choice.chooseTotp();
      }

      // Should now be on TOTP form
      await totpForm.expectVisible();
      const totpCode = generateTotpCode('user-both-options');
      await totpForm.enterCode(totpCode);
      await loginPage.expectLoggedIn();
    });
  });

  test.describe('Alternative Flow Behavior', () => {
    test('Email OTP works correctly in alternative flow', async ({ page }) => {
      const loginPage = new LoginPage(page);
      const otpForm = new OtpForm(page);

      await loginPage.goto(REALM);
      await loginPage.login('user-with-role', TEST_PASSWORD);

      await otpForm.expectVisible();

      // Verify code format
      const email = await mailpit.waitForMessage('user-with-role@test.local');
      const code = mailpit.extractOtpCode(email);

      expect(code).not.toBeNull();
      expect(code!.length).toBe(6);

      // Wrong code should fail
      await otpForm.enterCode('WRONG1');
      await otpForm.expectError();

      // Correct code should work
      await page.fill('#email-otp', '');
      await otpForm.enterCode(code!);
      await loginPage.expectLoggedIn();
    });
  });
});
