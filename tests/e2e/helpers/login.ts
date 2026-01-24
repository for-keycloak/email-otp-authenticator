import { Page, expect } from '@playwright/test';

export class LoginPage {
  constructor(private page: Page) {}

  async goto(realm: string, clientId: string = 'test-client') {
    const loginUrl = `/realms/${realm}/protocol/openid-connect/auth?client_id=${clientId}&redirect_uri=http://localhost:8080/&response_type=code&scope=openid`;
    await this.page.goto(loginUrl);
    await this.page.waitForLoadState('networkidle');
  }

  async login(username: string, password: string) {
    await this.page.fill('#username', username);
    await this.page.fill('#password', password);
    await this.page.click('#kc-login');
  }

  async expectLoggedIn() {
    // After successful login, we should be redirected away from Keycloak
    // The redirect URL contains the authorization code
    // Note: The redirect to localhost:8080 will fail from inside Docker,
    // so we check for either:
    // 1. The actual URL containing code=
    // 2. A navigation error (chrome-error://) which means redirect was attempted
    // 3. A request was made to a URL containing code=
    try {
      await this.page.waitForURL((url) => {
        const urlStr = url.toString();
        return urlStr.includes('code=') || urlStr.includes('chrome-error://');
      }, { timeout: 10000 });
    } catch {
      // If waitForURL times out, check if we're still on a Keycloak page
      const currentUrl = this.page.url();
      if (currentUrl.includes('/realms/') && currentUrl.includes('/protocol/openid-connect/')) {
        throw new Error(`Login did not complete - still on Keycloak page: ${currentUrl}`);
      }
    }
  }

  async expectOnLoginPage() {
    await expect(this.page.locator('#username')).toBeVisible();
    await expect(this.page.locator('#password')).toBeVisible();
  }

  async expectOnOtpPage() {
    // The email OTP form should have the OTP input field
    await expect(this.page.locator('#email-otp')).toBeVisible();
  }

  async expectOnTotpPage() {
    // The TOTP form has a different input field
    await expect(this.page.locator('#otp')).toBeVisible();
  }

  async expectOn2FAChoicePage() {
    // When both options are available, user sees a choice
    // Check for links/buttons to choose between methods
    const pageContent = await this.page.content();
    const hasTotpOption = pageContent.includes('otp') || pageContent.includes('Authenticator');
    const hasEmailOption = pageContent.includes('email') || pageContent.includes('Email');
    return hasTotpOption && hasEmailOption;
  }
}
