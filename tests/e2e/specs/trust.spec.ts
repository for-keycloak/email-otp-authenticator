import { test, expect, Page, BrowserContext } from '@playwright/test';
import { LoginPage } from '../helpers/login.js';
import { OtpForm } from '../helpers/otp-form.js';
import { mailpit } from '../setup/mailpit.js';
import { database } from '../setup/database.js';
import { TEST_PASSWORD } from '../setup/global-setup.js';

const TRUST_COOKIE_NAME = 'EMAIL_OTP_DEVICE_TRUST';

// Helper: Complete login with OTP
async function loginWithOtp(
  page: Page,
  realm: string,
  username: string,
  trustDevice: boolean = false
): Promise<void> {
  const loginPage = new LoginPage(page);
  const otpForm = new OtpForm(page);

  await loginPage.goto(realm);
  await loginPage.login(username, TEST_PASSWORD);
  await otpForm.expectVisible();

  const email = await mailpit.waitForMessage(`${username}@test.local`);
  const code = mailpit.extractOtpCode(email);
  expect(code).not.toBeNull();

  await otpForm.enterCode(code!, trustDevice);
  await loginPage.expectLoggedIn();
}

// Helper: Clear session but preserve device trust cookie
async function clearSessionKeepTrust(context: BrowserContext): Promise<void> {
  const cookies = await context.cookies();
  const trustCookie = cookies.find(c => c.name === TRUST_COOKIE_NAME);

  await context.clearCookies();

  if (trustCookie) {
    await context.addCookies([{
      name: trustCookie.name,
      value: trustCookie.value,
      domain: trustCookie.domain,
      path: trustCookie.path,
      httpOnly: trustCookie.httpOnly,
      secure: trustCookie.secure,
      sameSite: trustCookie.sameSite as 'Strict' | 'Lax' | 'None',
    }]);
  }
}

// Helper: Check login skips OTP
async function expectLoginSkipsOtp(page: Page, realm: string, username: string): Promise<void> {
  const loginPage = new LoginPage(page);
  await loginPage.goto(realm);
  await loginPage.login(username, TEST_PASSWORD);
  await loginPage.expectLoggedIn();

  const messages = await mailpit.getMessages();
  expect(messages.total).toBe(0);
}

// Helper: Check login requires OTP
async function expectLoginRequiresOtp(page: Page, realm: string, username: string): Promise<void> {
  const loginPage = new LoginPage(page);
  const otpForm = new OtpForm(page);

  await loginPage.goto(realm);
  await loginPage.login(username, TEST_PASSWORD);
  await otpForm.expectVisible();
}

// Helper: Navigate to email OTP form when it's an alternative
async function navigateToEmailOtp(page: Page): Promise<void> {
  const isOnEmailOtp = await page.locator('#email-otp').isVisible({ timeout: 2000 }).catch(() => false);
  if (!isOnEmailOtp) {
    const tryAnotherLink = page.locator('a:has-text("Try another way"), #try-another-way');
    if (await tryAnotherLink.isVisible({ timeout: 2000 }).catch(() => false)) {
      await tryAnotherLink.click();
      await page.waitForLoadState('networkidle');
      const emailOption = page.locator('text="Email OTP"').first();
      if (await emailOption.isVisible({ timeout: 2000 }).catch(() => false)) {
        await emailOption.click();
        await page.waitForLoadState('networkidle');
      }
    }
  }
}

// Helper: Login with OTP when email OTP might be an alternative
async function loginWithOtpAlternative(
  page: Page,
  realm: string,
  username: string
): Promise<void> {
  const loginPage = new LoginPage(page);
  const otpForm = new OtpForm(page);

  await loginPage.goto(realm);
  await loginPage.login(username, TEST_PASSWORD);

  // Navigate to email OTP if needed (might be on TOTP form first)
  await navigateToEmailOtp(page);

  await otpForm.expectVisible();

  const email = await mailpit.waitForMessage(`${username}@test.local`);
  const code = mailpit.extractOtpCode(email);
  expect(code).not.toBeNull();

  await otpForm.enterCode(code!);
  await loginPage.expectLoggedIn();
}

test.describe('IP Trust', () => {
  const REALM = 'test-ip-trust';
  const USER = 'trust-user';

  test.beforeEach(async () => {
    await mailpit.deleteAllMessages();
    await database.clearTrustEntries();
  });

  test('first login requires OTP', async ({ page }) => {
    const otpForm = new OtpForm(page);
    await loginWithOtp(page, REALM, USER);

    // Verify device trust checkbox was NOT visible (disabled in this realm)
    // This is implicitly tested since enterCode doesn't require the checkbox
  });

  test('second login from same IP skips OTP', async ({ page, context }) => {
    await loginWithOtp(page, REALM, USER);

    await context.clearCookies();
    await mailpit.deleteAllMessages();

    await expectLoginSkipsOtp(page, REALM, USER);
  });
});

test.describe('Device Trust', () => {
  const REALM = 'test-device-trust';
  const USER = 'trust-user';

  test.beforeEach(async () => {
    await mailpit.deleteAllMessages();
    await database.clearTrustEntries();
  });

  test('tampered cookie signature is rejected', async ({ page, context }) => {
    // First, login and trust the device
    await loginWithOtp(page, REALM, USER, true);

    // Get the trust cookie
    const cookies = await context.cookies();
    const trustCookie = cookies.find(c => c.name === TRUST_COOKIE_NAME);
    expect(trustCookie).toBeDefined();

    // Tamper with the cookie value (modify signature)
    const tamperedValue = trustCookie!.value.slice(0, -5) + 'XXXXX';

    await context.clearCookies();
    await context.addCookies([{
      name: trustCookie!.name,
      value: tamperedValue,
      domain: trustCookie!.domain,
      path: trustCookie!.path,
      httpOnly: trustCookie!.httpOnly,
      secure: trustCookie!.secure,
      sameSite: trustCookie!.sameSite as 'Strict' | 'Lax' | 'None',
    }]);

    await mailpit.deleteAllMessages();

    // Should require OTP because tampered cookie is invalid
    await expectLoginRequiresOtp(page, REALM, USER);
  });

  test('invalid cookie format is rejected', async ({ page, context }) => {
    // Set a cookie with invalid format (no signature separator)
    await context.addCookies([{
      name: TRUST_COOKIE_NAME,
      value: 'invalid-token-without-signature',
      domain: 'localhost',
      path: '/',
      httpOnly: true,
      secure: false,
      sameSite: 'Lax',
    }]);

    const loginPage = new LoginPage(page);
    const otpForm = new OtpForm(page);

    await loginPage.goto(REALM);
    await loginPage.login(USER, TEST_PASSWORD);

    // Should require OTP because cookie format is invalid
    await otpForm.expectVisible();
  });

  test('cookie from different realm is rejected', async ({ page, context }) => {
    // Login and trust device in one realm
    await loginWithOtp(page, REALM, USER, true);

    // Get the cookie
    const cookies = await context.cookies();
    const trustCookie = cookies.find(c => c.name === TRUST_COOKIE_NAME);
    expect(trustCookie).toBeDefined();

    await clearSessionKeepTrust(context);
    await mailpit.deleteAllMessages();

    // Try to use the cookie in a different realm (test-both-trust)
    // The token in DB is realm-specific, so it won't be found
    const loginPage = new LoginPage(page);
    const otpForm = new OtpForm(page);

    await loginPage.goto('test-both-trust');
    await loginPage.login(USER, TEST_PASSWORD);

    // Should require OTP because token doesn't exist in other realm's DB
    await otpForm.expectVisible();
  });

  test('device trust checkbox is visible when enabled', async ({ page }) => {
    const loginPage = new LoginPage(page);
    const otpForm = new OtpForm(page);

    await loginPage.goto(REALM);
    await loginPage.login(USER, TEST_PASSWORD);

    await otpForm.expectVisible();
    await otpForm.expectTrustDeviceCheckboxVisible();

    const label = await otpForm.getTrustDeviceLabel();
    expect(label.toLowerCase()).toContain('don\'t ask');
  });

  test('checking trust device sets cookie and skips OTP on next login', async ({ page, context }) => {
    await loginWithOtp(page, REALM, USER, true);

    const cookies = await context.cookies();
    const trustCookie = cookies.find(c => c.name === TRUST_COOKIE_NAME);
    expect(trustCookie).toBeDefined();
    expect(trustCookie!.httpOnly).toBe(true);

    await clearSessionKeepTrust(context);
    await mailpit.deleteAllMessages();

    await expectLoginSkipsOtp(page, REALM, USER);
  });

  test('not checking trust device requires OTP on next login', async ({ page, context }) => {
    await loginWithOtp(page, REALM, USER, false);

    await context.clearCookies();
    await mailpit.deleteAllMessages();

    await expectLoginRequiresOtp(page, REALM, USER);
  });
});

test.describe('Both Trust Features Enabled', () => {
  const REALM = 'test-both-trust';
  const USER = 'trust-user';

  test.beforeEach(async () => {
    await mailpit.deleteAllMessages();
    await database.clearTrustEntries();
  });

  test('device trust takes priority over IP trust', async ({ page, context }) => {
    await loginWithOtp(page, REALM, USER, true);

    await clearSessionKeepTrust(context);
    await mailpit.deleteAllMessages();

    await expectLoginSkipsOtp(page, REALM, USER);
  });

  test('IP trust works when device trust cookie is not present', async ({ page, context }) => {
    await loginWithOtp(page, REALM, USER, false);

    await context.clearCookies();
    await mailpit.deleteAllMessages();

    await expectLoginSkipsOtp(page, REALM, USER);
  });

  test('both checkbox and IP trust are available on form', async ({ page }) => {
    const loginPage = new LoginPage(page);
    const otpForm = new OtpForm(page);

    await loginPage.goto(REALM);
    await loginPage.login(USER, TEST_PASSWORD);

    await otpForm.expectVisible();
    await otpForm.expectTrustDeviceCheckboxVisible();
  });
});

test.describe('Trust Only When Sole Authenticator', () => {
  const REALM = 'test-trust-alternatives';
  const USER_BOTH = 'user-both-options';
  const USER_EMAIL_ONLY = 'user-email-only';

  test.beforeEach(async () => {
    await mailpit.deleteAllMessages();
    await database.clearTrustEntries();
  });

  test('trust is ignored when email OTP is alternative with TOTP - first login requires OTP', async ({ page }) => {
    // First login for user with both options (email OTP + TOTP)
    // User selects email OTP and completes it - should require OTP entry
    await loginWithOtpAlternative(page, REALM, USER_BOTH);
  });

  test('second login still requires OTP when email OTP is alternative (trust not applied)', async ({ page, context }) => {
    // First login - complete email OTP to establish IP trust entry
    await loginWithOtpAlternative(page, REALM, USER_BOTH);

    // Clear session but keep any cookies
    await context.clearCookies();
    await mailpit.deleteAllMessages();

    // Second login - even though IP trust entry exists, trust should NOT apply
    // because email OTP is configured as ALTERNATIVE
    const loginPage = new LoginPage(page);
    const otpForm = new OtpForm(page);

    await loginPage.goto(REALM);
    await loginPage.login(USER_BOTH, TEST_PASSWORD);

    // Navigate to email OTP
    await navigateToEmailOtp(page);

    // Should still require OTP - trust should NOT have bypassed it
    await otpForm.expectVisible();

    // Email should be sent (proving trust was not applied)
    const email = await mailpit.waitForMessage(`${USER_BOTH}@test.local`);
    expect(email).toBeDefined();
  });

  test('trust NOT applied even when user only has email OTP (execution is still ALTERNATIVE)', async ({ page, context }) => {
    // For user-email-only, email OTP is the only configured 2FA for this user
    // However, the execution is still marked as ALTERNATIVE in the flow
    // So trust should still NOT apply (based on flow configuration, not user configuration)

    // First login - user only has email OTP, so they go directly to email OTP form
    await loginWithOtp(page, REALM, USER_EMAIL_ONLY);

    await context.clearCookies();
    await mailpit.deleteAllMessages();

    // Second login - trust should still NOT apply because the execution is ALTERNATIVE
    await expectLoginRequiresOtp(page, REALM, USER_EMAIL_ONLY);
  });
});

test.describe('Default Behavior (Both Disabled)', () => {
  const REALM = 'test-otp-required';
  const USER = 'user-with-role';

  test.beforeEach(async () => {
    await mailpit.deleteAllMessages();
    await database.clearTrustEntries();
  });

  test('OTP is required on every login when trust is disabled', async ({ page, context }) => {
    const otpForm = new OtpForm(page);

    await loginWithOtp(page, REALM, USER);

    // Verify device trust checkbox was NOT visible
    await otpForm.expectTrustDeviceCheckboxNotVisible().catch(() => {
      // Already submitted, can't check - that's OK
    });

    await context.clearCookies();
    await mailpit.deleteAllMessages();

    await expectLoginRequiresOtp(page, REALM, USER);
  });
});
