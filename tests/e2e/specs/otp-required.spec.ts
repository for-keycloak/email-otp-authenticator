import { test, expect } from '@playwright/test';
import { LoginPage } from '../helpers/login.js';
import { OtpForm } from '../helpers/otp-form.js';
import { mailpit } from '../setup/mailpit.js';
import { TEST_PASSWORD } from '../setup/global-setup.js';

const REALM = 'test-otp-required';
const REALM_SHORT_TTL = 'test-short-ttl';
const DEFAULT_ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
const DEFAULT_CODE_LENGTH = 6;

test.describe('Email OTP Required Flow', () => {
  test.beforeEach(async () => {
    // Clear emails before each test
    await mailpit.deleteAllMessages();
  });

  test.describe('OTP Code Validation', () => {
    test('OTP code matches configured alphabet and length', async ({ page }) => {
      const loginPage = new LoginPage(page);
      const otpForm = new OtpForm(page);

      await loginPage.goto(REALM);
      await loginPage.login('user-with-role', TEST_PASSWORD);

      // Should be on OTP page
      await otpForm.expectVisible();

      // Wait for email and extract code
      const email = await mailpit.waitForMessage('user-with-role@test.local');
      const code = mailpit.extractOtpCode(email);

      expect(code).not.toBeNull();
      expect(code!.length).toBe(DEFAULT_CODE_LENGTH);

      // Verify all characters are from the configured alphabet
      for (const char of code!) {
        expect(DEFAULT_ALPHABET).toContain(char);
      }
    });

    test('correct OTP code allows login', async ({ page }) => {
      const loginPage = new LoginPage(page);
      const otpForm = new OtpForm(page);

      await loginPage.goto(REALM);
      await loginPage.login('user-with-role', TEST_PASSWORD);

      await otpForm.expectVisible();

      // Get OTP from email
      const email = await mailpit.waitForMessage('user-with-role@test.local');
      const code = mailpit.extractOtpCode(email);
      expect(code).not.toBeNull();

      // Enter correct code
      await otpForm.enterCode(code!);

      // Should be logged in
      await loginPage.expectLoggedIn();
    });

    test('incorrect OTP code is rejected', async ({ page }) => {
      const loginPage = new LoginPage(page);
      const otpForm = new OtpForm(page);

      await loginPage.goto(REALM);
      await loginPage.login('user-with-role', TEST_PASSWORD);

      await otpForm.expectVisible();

      // Wait for email to ensure OTP was generated
      await mailpit.waitForMessage('user-with-role@test.local');

      // Enter wrong code
      await otpForm.enterCode('WRONG1');

      // Should still be on OTP page with error
      await otpForm.expectVisible();
      await otpForm.expectError();
    });

    test('empty OTP submission is rejected', async ({ page }) => {
      const loginPage = new LoginPage(page);
      const otpForm = new OtpForm(page);

      await loginPage.goto(REALM);
      await loginPage.login('user-with-role', TEST_PASSWORD);

      await otpForm.expectVisible();
      await mailpit.waitForMessage('user-with-role@test.local');

      // Submit without entering any code
      await otpForm.submitEmpty();

      // Should still be on OTP page with error
      await otpForm.expectVisible();
      await otpForm.expectError();
    });

    test('OTP with leading/trailing whitespace is rejected', async ({ page }) => {
      const loginPage = new LoginPage(page);
      const otpForm = new OtpForm(page);

      await loginPage.goto(REALM);
      await loginPage.login('user-with-role', TEST_PASSWORD);

      await otpForm.expectVisible();

      const email = await mailpit.waitForMessage('user-with-role@test.local');
      const code = mailpit.extractOtpCode(email);
      expect(code).not.toBeNull();

      // Enter code with whitespace
      await otpForm.enterCode('  ' + code! + '  ');

      // Should be rejected (whitespace not trimmed)
      await otpForm.expectVisible();
      await otpForm.expectError();
    });

    test('lowercase OTP code is rejected (case sensitive)', async ({ page }) => {
      const loginPage = new LoginPage(page);
      const otpForm = new OtpForm(page);

      await loginPage.goto(REALM);
      await loginPage.login('user-with-role', TEST_PASSWORD);

      await otpForm.expectVisible();

      const email = await mailpit.waitForMessage('user-with-role@test.local');
      const code = mailpit.extractOtpCode(email);
      expect(code).not.toBeNull();

      // Enter lowercase version
      await otpForm.enterCode(code!.toLowerCase());

      // Should be rejected (case sensitive)
      await otpForm.expectVisible();
      await otpForm.expectError();
    });

    test('code regeneration on retry - old code becomes invalid', async ({
      page,
    }) => {
      const loginPage = new LoginPage(page);
      const otpForm = new OtpForm(page);

      // First login attempt
      await loginPage.goto(REALM);
      await loginPage.login('user-with-role', TEST_PASSWORD);

      await otpForm.expectVisible();

      // Get first OTP
      const email1 = await mailpit.waitForMessage('user-with-role@test.local');
      const code1 = mailpit.extractOtpCode(email1);
      expect(code1).not.toBeNull();

      // Clear emails
      await mailpit.deleteAllMessages();

      // Navigate away (abort login)
      await loginPage.goto(REALM);
      await loginPage.login('user-with-role', TEST_PASSWORD);

      await otpForm.expectVisible();

      // Get second OTP
      const email2 = await mailpit.waitForMessage('user-with-role@test.local');
      const code2 = mailpit.extractOtpCode(email2);
      expect(code2).not.toBeNull();

      // Codes should be different
      expect(code1).not.toBe(code2);

      // Old code should not work
      await otpForm.enterCode(code1!);
      await otpForm.expectVisible();
      await otpForm.expectError();

      // Clear the form and try new code
      await page.fill('#email-otp', '');
      await otpForm.enterCode(code2!);

      // New code should work
      await loginPage.expectLoggedIn();
    });
  });

  test.describe('Role Filtering', () => {
    test('user WITH role sees OTP step', async ({ page }) => {
      const loginPage = new LoginPage(page);
      const otpForm = new OtpForm(page);

      await loginPage.goto(REALM);
      await loginPage.login('user-with-role', TEST_PASSWORD);

      // Should be on OTP page
      await otpForm.expectVisible();

      // Should have received an email
      const email = await mailpit.waitForMessage('user-with-role@test.local');
      expect(email).toBeDefined();
    });

    test('user WITHOUT role skips OTP step', async ({ page }) => {
      const loginPage = new LoginPage(page);

      await loginPage.goto(REALM);
      await loginPage.login('user-without-role', TEST_PASSWORD);

      // Should be logged in directly, no OTP step
      await loginPage.expectLoggedIn();

      // Should NOT have received an email
      const messages = await mailpit.getMessages();
      const emailToUser = messages.messages.find((m) =>
        m.To.some((t) => t.Address === 'user-without-role@test.local')
      );
      expect(emailToUser).toBeUndefined();
    });
  });

  test.describe('Resend Functionality', () => {
    test('clicking resend generates new code', async ({ page }) => {
      const loginPage = new LoginPage(page);
      const otpForm = new OtpForm(page);

      await loginPage.goto(REALM);
      await loginPage.login('user-with-role', TEST_PASSWORD);

      await otpForm.expectVisible();

      // Get first OTP
      const email1 = await mailpit.waitForMessage('user-with-role@test.local');
      const code1 = mailpit.extractOtpCode(email1);

      // Clear emails
      await mailpit.deleteAllMessages();

      // Click resend
      await otpForm.clickResend();

      // Wait for new email
      const email2 = await mailpit.waitForMessage('user-with-role@test.local');
      const code2 = mailpit.extractOtpCode(email2);

      // Codes should be different
      expect(code1).not.toBe(code2);

      // New code should work
      await otpForm.enterCode(code2!);
      await loginPage.expectLoggedIn();
    });
  });
});

test.describe('OTP Expiration', () => {
  test.beforeEach(async () => {
    await mailpit.deleteAllMessages();
  });

  test('expired OTP code is rejected and new code is sent', async ({ page }) => {
    const loginPage = new LoginPage(page);
    const otpForm = new OtpForm(page);

    await loginPage.goto(REALM_SHORT_TTL);
    await loginPage.login('ttl-user', TEST_PASSWORD);

    await otpForm.expectVisible();

    // Get the OTP code
    const email1 = await mailpit.waitForMessage('ttl-user@test.local');
    const code1 = mailpit.extractOtpCode(email1);
    expect(code1).not.toBeNull();

    // Wait for the code to expire (TTL is 3 seconds, wait 4 to be safe)
    await page.waitForTimeout(4000);

    // Clear emails to detect new one
    await mailpit.deleteAllMessages();

    // Try to use the expired code
    await otpForm.enterCode(code1!);

    // Should show expiration error and remain on OTP page
    await otpForm.expectVisible();
    await otpForm.expectError();

    // A new code should have been sent automatically
    const email2 = await mailpit.waitForMessage('ttl-user@test.local');
    const code2 = mailpit.extractOtpCode(email2);
    expect(code2).not.toBeNull();
    expect(code2).not.toBe(code1);

    // New code should work
    await otpForm.clearCode();
    await otpForm.enterCode(code2!);
    await loginPage.expectLoggedIn();
  });
});
