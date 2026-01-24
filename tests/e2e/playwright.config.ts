import { defineConfig, devices } from '@playwright/test';

const KEYCLOAK_URL = process.env.KEYCLOAK_URL || 'http://localhost:8080';

export default defineConfig({
  testDir: './specs',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [['html', { open: 'never' }]],

  globalSetup: './setup/global-setup.ts',

  use: {
    baseURL: KEYCLOAK_URL,
    trace: 'on-first-retry',
    headless: true,
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
