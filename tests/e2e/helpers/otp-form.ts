import { Page, expect } from '@playwright/test';

export class OtpForm {
  constructor(private page: Page) {}

  async enterCode(code: string, trustDevice: boolean = false) {
    await this.page.fill('#email-otp', code);
    if (trustDevice) {
      await this.checkTrustDevice();
    }
    await this.page.click('#kc-login');
  }

  async submitEmpty() {
    await this.page.click('#kc-login');
  }

  async clearCode() {
    await this.page.fill('#email-otp', '');
  }

  async clickResend() {
    await this.page.click('#kc-resend-email');
  }

  async expectVisible() {
    await expect(this.page.locator('#email-otp')).toBeVisible();
  }

  async expectError() {
    // Check for error message on the page
    const errorElement = this.page.locator('.alert-error, .kc-feedback-text, [class*="error"]');
    await expect(errorElement).toBeVisible();
  }

  async expectInvalidCodeError() {
    // The specific error message for invalid OTP
    const pageContent = await this.page.content();
    expect(pageContent.toLowerCase()).toContain('invalid');
  }

  async expectTrustDeviceCheckboxVisible() {
    await expect(this.page.locator('#trust-device')).toBeVisible();
  }

  async expectTrustDeviceCheckboxNotVisible() {
    await expect(this.page.locator('#trust-device')).not.toBeVisible();
  }

  async checkTrustDevice() {
    await this.page.check('#trust-device');
  }

  async isTrustDeviceChecked(): Promise<boolean> {
    return this.page.isChecked('#trust-device');
  }

  async getTrustDeviceLabel(): Promise<string> {
    return this.page.locator('label[for="trust-device"]').innerText();
  }
}

export class TotpForm {
  constructor(private page: Page) {}

  async enterCode(code: string) {
    await this.page.fill('#otp', code);
    await this.page.click('#kc-login');
  }

  async expectVisible() {
    await expect(this.page.locator('#otp')).toBeVisible();
  }
}

// Helper to choose between 2FA methods when both are available
export class TwoFactorChoice {
  constructor(private page: Page) {}

  async clickTryAnotherWay(): Promise<boolean> {
    // Keycloak shows "Try another way" link when alternatives are available
    const tryAnotherLink = this.page.locator('a:has-text("Try another way"), a:has-text("try another way"), #try-another-way');
    if (await tryAnotherLink.isVisible({ timeout: 2000 }).catch(() => false)) {
      await tryAnotherLink.click();
      await this.page.waitForLoadState('networkidle');
      return true;
    }
    return false;
  }

  async chooseEmailOtp() {
    // First try to click "Try another way" if we're on TOTP form
    await this.clickTryAnotherWay();

    // Now look for email OTP option in the list
    // Keycloak shows options as divs with text like "Email OTP" and description
    const selectors = [
      'text="Email OTP"',
      ':has-text("Email OTP")',
      'a:has-text("Email")',
      '[id*="email"]',
      'div:has-text("email address")',
    ];

    for (const selector of selectors) {
      const element = this.page.locator(selector).first();
      if (await element.isVisible({ timeout: 1000 }).catch(() => false)) {
        await element.click();
        await this.page.waitForLoadState('networkidle');
        return;
      }
    }
  }

  async chooseTotp() {
    // First try to click "Try another way" if we're on email OTP form
    await this.clickTryAnotherWay();

    // Look for TOTP/Authenticator option
    const selectors = [
      'text="Authenticator Application"',
      ':has-text("Authenticator Application")',
      'a:has-text("Authenticator")',
      'div:has-text("authenticator application")',
    ];

    for (const selector of selectors) {
      const element = this.page.locator(selector).first();
      if (await element.isVisible({ timeout: 1000 }).catch(() => false)) {
        await element.click();
        await this.page.waitForLoadState('networkidle');
        return;
      }
    }
  }

  async isEmailOtpOptionVisible(): Promise<boolean> {
    // Check specifically for Email OTP in the authentication method selection list
    // Keycloak shows options as clickable divs/links with the authenticator display name
    const selectors = [
      '.select-auth-box-parent:has-text("Email OTP")',
      '.select-auth-box-desc:has-text("Email")',
      '#kc-select-credential-form :has-text("Email OTP")',
      'a[id*="email-otp"]',
      'div.pf-c-tile:has-text("Email")',
    ];

    for (const selector of selectors) {
      const element = this.page.locator(selector).first();
      if (await element.isVisible({ timeout: 1000 }).catch(() => false)) {
        return true;
      }
    }
    return false;
  }

  async isTryAnotherWayVisible(): Promise<boolean> {
    const tryAnotherLink = this.page.locator('a:has-text("Try another way"), a:has-text("try another way"), #try-another-way');
    return await tryAnotherLink.isVisible({ timeout: 2000 }).catch(() => false);
  }
}
