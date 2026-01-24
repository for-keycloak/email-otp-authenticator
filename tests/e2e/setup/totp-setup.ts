import { chromium, Browser } from '@playwright/test';
import * as OTPAuth from 'otpauth';
import * as fs from 'fs';
import * as path from 'path';
import { keycloakAdmin } from './keycloak-admin.js';

const KEYCLOAK_URL = process.env.KEYCLOAK_URL || 'http://localhost:8080';
const SECRETS_FILE = path.join(process.cwd(), '.totp-secrets.json');

interface TotpSecrets {
  [username: string]: string;
}

let browser: Browser | null = null;

async function getBrowser(): Promise<Browser> {
  if (!browser) {
    browser = await chromium.launch({ headless: true });
  }
  return browser;
}

export async function closeBrowser(): Promise<void> {
  if (browser) {
    await browser.close();
    browser = null;
  }
}

/**
 * Sets up TOTP for a user via the Keycloak Required Action flow.
 */
export async function setupTotpViaUI(
  realmName: string,
  username: string,
  password: string
): Promise<string> {
  const browser = await getBrowser();
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    // Get user ID and add CONFIGURE_TOTP required action
    const usersResponse = await fetch(
      `${KEYCLOAK_URL}/admin/realms/${realmName}/users?username=${username}&exact=true`,
      {
        headers: {
          Authorization: `Bearer ${await keycloakAdmin.getAccessToken()}`,
        },
      }
    );
    const users = await usersResponse.json();
    const userId = users[0].id;

    await keycloakAdmin.enableRequiredAction(realmName, 'CONFIGURE_TOTP');
    await keycloakAdmin.addUserRequiredAction(realmName, userId, 'CONFIGURE_TOTP');

    // Login to trigger TOTP setup
    const redirectUri = encodeURIComponent(`${KEYCLOAK_URL}/realms/${realmName}/account/`);
    await page.goto(`${KEYCLOAK_URL}/realms/${realmName}/protocol/openid-connect/auth?client_id=test-client&redirect_uri=${redirectUri}&response_type=code&scope=openid`);
    await page.waitForLoadState('networkidle');

    const usernameInput = page.locator('input[name="username"], #username');
    if (await usernameInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await usernameInput.fill(username);
      await page.locator('input[name="password"], #password').fill(password);
      await page.locator('input[type="submit"], button[type="submit"], #kc-login').click();
      await page.waitForLoadState('networkidle');
    }

    await page.waitForTimeout(1000);

    // Click "Unable to scan?" to reveal the secret key
    const unableToScanLink = page.locator('a:has-text("Unable to scan"), a:has-text("unable to scan"), a:has-text("Can\'t scan")').first();
    if (await unableToScanLink.isVisible({ timeout: 3000 }).catch(() => false)) {
      await unableToScanLink.click();
      await page.waitForTimeout(500);
    }

    // Extract the TOTP secret
    let secret: string | null = null;

    const secretSelectors = [
      '#kc-totp-secret-key',
      '[id*="secret"]',
      'span.kc-totp-secret-key',
      '.otp-secret',
      'code',
    ];

    for (const selector of secretSelectors) {
      const element = page.locator(selector).first();
      if (await element.isVisible({ timeout: 2000 }).catch(() => false)) {
        const text = await element.textContent() || '';
        const cleanText = text.replace(/\s/g, '');
        const match = cleanText.match(/^([A-Z2-7]{16,64})$/);
        if (match) {
          secret = match[1];
          break;
        }
      }
    }

    // Fallback: search page text for base32 pattern
    if (!secret) {
      secret = await page.evaluate(() => {
        const text = document.body.innerText;
        const matches = text.match(/([A-Z2-7]{4}\s*){4,8}/g);
        if (matches && matches.length > 0) {
          return matches[0].replace(/\s/g, '');
        }
        const noSpaceMatches = text.match(/\b([A-Z2-7]{16,64})\b/g);
        return noSpaceMatches?.[0] || null;
      });
    }

    if (!secret) {
      throw new Error(`Could not extract TOTP secret from page for ${username}`);
    }

    // Generate and submit TOTP code
    const totp = new OTPAuth.TOTP({
      issuer: 'Keycloak',
      label: username,
      algorithm: 'SHA1',
      digits: 6,
      period: 30,
      secret: secret,
    });
    const code = totp.generate();

    const otpInput = page.locator('input#totp, input[name="totp"], input#otp');
    await otpInput.fill(code);

    const deviceNameInput = page.locator('input#userLabel, input[name="userLabel"]');
    if (await deviceNameInput.isVisible({ timeout: 1000 }).catch(() => false)) {
      await deviceNameInput.fill('Test Device');
    }

    const submitButton = page.locator('input[type="submit"], button[type="submit"]').first();
    await submitButton.click();
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Check for errors
    const errorMessage = page.locator('.alert-error, .kc-feedback-text, [class*="error"]');
    if (await errorMessage.isVisible({ timeout: 1000 }).catch(() => false)) {
      const errorText = await errorMessage.textContent();
      if (errorText && errorText.toLowerCase().includes('error')) {
        throw new Error(`TOTP setup failed for ${username}: ${errorText}`);
      }
    }

    saveTotpSecret(username, secret);
    return secret;
  } finally {
    await context.close();
  }
}

function saveTotpSecret(username: string, secret: string): void {
  let secrets: TotpSecrets = {};
  if (fs.existsSync(SECRETS_FILE)) {
    secrets = JSON.parse(fs.readFileSync(SECRETS_FILE, 'utf-8'));
  }
  secrets[username] = secret;
  fs.writeFileSync(SECRETS_FILE, JSON.stringify(secrets, null, 2));
}

export function getTotpSecret(username: string): string | null {
  if (fs.existsSync(SECRETS_FILE)) {
    const secrets: TotpSecrets = JSON.parse(fs.readFileSync(SECRETS_FILE, 'utf-8'));
    return secrets[username] || null;
  }
  return null;
}

export function cleanupSecrets(): void {
  if (fs.existsSync(SECRETS_FILE)) {
    fs.unlinkSync(SECRETS_FILE);
  }
}
