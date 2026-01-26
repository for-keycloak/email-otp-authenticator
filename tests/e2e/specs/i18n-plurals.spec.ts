import { test, expect, Page } from '@playwright/test';
import { LoginPage } from '../helpers/login.js';
import { OtpForm } from '../helpers/otp-form.js';
import { keycloakAdmin } from '../setup/keycloak-admin.js';
import { mailpit } from '../setup/mailpit.js';
import { TEST_PASSWORD } from '../setup/global-setup.js';

const REALM = 'test-i18n';
const USER = 'i18n-user';

// Helper to set user locale and navigate to OTP form
async function navigateToOtpFormWithLocale(
  page: Page,
  locale: string
): Promise<OtpForm> {
  // Set user locale via admin API
  const user = await keycloakAdmin.getUserByUsername(REALM, USER);
  if (!user) {
    throw new Error(`User ${USER} not found`);
  }
  await keycloakAdmin.setUserLocale(REALM, user.id, locale);

  const loginPage = new LoginPage(page);
  const otpForm = new OtpForm(page);

  await loginPage.goto(REALM);
  await loginPage.login(USER, TEST_PASSWORD);
  await otpForm.expectVisible();

  return otpForm;
}

test.describe('Internationalization - Plural Forms', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(async () => {
    await mailpit.deleteAllMessages();
  });

  test.afterEach(async ({ context }) => {
    await context.clearCookies();
  });

  test('English shows "3 days" (simple plural)', async ({ page }) => {
    const otpForm = await navigateToOtpFormWithLocale(page, 'en');
    await otpForm.expectTrustDeviceCheckboxVisible();

    const label = await otpForm.getTrustDeviceLabel();
    // English: "Don't ask for a code on this device for 3 days"
    expect(label.toLowerCase()).toContain('3');
    expect(label.toLowerCase()).toContain('day');
  });

  test('German shows "3 Tage" (plural)', async ({ page }) => {
    const otpForm = await navigateToOtpFormWithLocale(page, 'de');
    await otpForm.expectTrustDeviceCheckboxVisible();

    const label = await otpForm.getTrustDeviceLabel();
    // German: "Auf diesem Gerät 3 Tage lang nicht nach einem Code fragen"
    expect(label).toContain('3');
    expect(label.toLowerCase()).toMatch(/tag/i);
  });

  test('French shows "3 jours" (simple plural)', async ({ page }) => {
    const otpForm = await navigateToOtpFormWithLocale(page, 'fr');
    await otpForm.expectTrustDeviceCheckboxVisible();

    const label = await otpForm.getTrustDeviceLabel();
    // French: "Ne pas demander de code sur cet appareil pendant 3 jours"
    expect(label).toContain('3');
    expect(label.toLowerCase()).toContain('jour');
  });

  test('Russian shows "3 дня" (few form, not "дней")', async ({ page }) => {
    const otpForm = await navigateToOtpFormWithLocale(page, 'ru');
    await otpForm.expectTrustDeviceCheckboxVisible();

    const label = await otpForm.getTrustDeviceLabel();
    // Russian: 3 uses "few" form -> "дня" (not "день" for 1, not "дней" for 5+)
    expect(label).toContain('3');
    expect(label).toContain('дня'); // few form
    expect(label).not.toContain('дней'); // many form
  });

  test('Polish shows "3 dni" (few form)', async ({ page }) => {
    const otpForm = await navigateToOtpFormWithLocale(page, 'pl');
    await otpForm.expectTrustDeviceCheckboxVisible();

    const label = await otpForm.getTrustDeviceLabel();
    // Polish: 3 uses "few" form -> "dni"
    expect(label).toContain('3');
    expect(label).toContain('dni');
  });

  test('Czech shows "3 dny" (few form)', async ({ page }) => {
    const otpForm = await navigateToOtpFormWithLocale(page, 'cs');
    await otpForm.expectTrustDeviceCheckboxVisible();

    const label = await otpForm.getTrustDeviceLabel();
    // Czech: 3 uses "few" form -> "dny"
    expect(label).toContain('3');
    expect(label).toContain('dny');
  });

  test('Ukrainian shows "3 дні" (few form)', async ({ page }) => {
    const otpForm = await navigateToOtpFormWithLocale(page, 'uk');
    await otpForm.expectTrustDeviceCheckboxVisible();

    const label = await otpForm.getTrustDeviceLabel();
    // Ukrainian: 3 uses "few" form -> "дні"
    expect(label).toContain('3');
    expect(label).toContain('дні');
  });

  test('Slovenian shows "3 dni" (few form for 3-4)', async ({ page }) => {
    const otpForm = await navigateToOtpFormWithLocale(page, 'sl');
    await otpForm.expectTrustDeviceCheckboxVisible();

    const label = await otpForm.getTrustDeviceLabel();
    // Slovenian: 3 uses "few" form -> "dni"
    expect(label).toContain('3');
    expect(label).toContain('dni');
  });

  test('Arabic shows "3 أيام" (few form for 3-10)', async ({ page }) => {
    const otpForm = await navigateToOtpFormWithLocale(page, 'ar');
    await otpForm.expectTrustDeviceCheckboxVisible();

    const label = await otpForm.getTrustDeviceLabel();
    // Arabic: 3 uses "few" form -> "أيام" (for 3-10)
    // Note: Some Keycloak versions use Arabic-Indic numerals (٣), others use Western (3)
    expect(label).toMatch(/[3٣]/);
    expect(label).toContain('أيام'); // few form (3-10)
  });
});
