import { keycloakAdmin } from './keycloak-admin.js';
import { mailpit } from './mailpit.js';
import { setupTotpViaUI, closeBrowser, cleanupSecrets } from './totp-setup.js';

const TEST_PASSWORD = 'testpassword';
const OTP_ROLE = 'otp-required';

export default async function globalSetup() {
  try {
    await mailpit.deleteAllMessages();
    cleanupSecrets();
    await setupRequiredRealm();
    await setupAlternativeRealm();
    await setupIpTrustRealm();
    await setupDeviceTrustRealm();
    await setupBothTrustRealm();
    await setupTrustWithAlternativesRealm();
    await setupShortTtlRealm();
    await setupI18nRealm();
  } finally {
    await closeBrowser();
  }
}

async function setupRequiredRealm() {
  const realmName = 'test-otp-required';

  if (await keycloakAdmin.realmExists(realmName)) {
    await keycloakAdmin.deleteRealm(realmName);
  }

  await keycloakAdmin.createRealm(realmName);
  await keycloakAdmin.configureSmtp(realmName);
  await keycloakAdmin.createTestClient(realmName);
  await keycloakAdmin.createRole(realmName, OTP_ROLE);

  const userWithRoleId = await keycloakAdmin.createUser(
    realmName,
    'user-with-role',
    'user-with-role@test.local',
    TEST_PASSWORD
  );
  await keycloakAdmin.assignRoleToUser(realmName, userWithRoleId, OTP_ROLE);

  await keycloakAdmin.createUser(
    realmName,
    'user-without-role',
    'user-without-role@test.local',
    TEST_PASSWORD
  );

  await setupRequiredFlow(realmName);
}

async function setupAlternativeRealm() {
  const realmName = 'test-otp-alternative';

  if (await keycloakAdmin.realmExists(realmName)) {
    await keycloakAdmin.deleteRealm(realmName);
  }

  await keycloakAdmin.createRealm(realmName);
  await keycloakAdmin.configureSmtp(realmName);
  await keycloakAdmin.createTestClient(realmName);
  await keycloakAdmin.createRole(realmName, OTP_ROLE);

  const userWithRoleId = await keycloakAdmin.createUser(
    realmName,
    'user-with-role',
    'user-with-role@test.local',
    TEST_PASSWORD
  );
  await keycloakAdmin.assignRoleToUser(realmName, userWithRoleId, OTP_ROLE);

  await keycloakAdmin.createUser(
    realmName,
    'user-without-role',
    'user-without-role@test.local',
    TEST_PASSWORD
  );

  await keycloakAdmin.createUser(
    realmName,
    'user-totp-only',
    'user-totp-only@test.local',
    TEST_PASSWORD
  );

  const userBothId = await keycloakAdmin.createUser(
    realmName,
    'user-both-options',
    'user-both-options@test.local',
    TEST_PASSWORD
  );
  await keycloakAdmin.assignRoleToUser(realmName, userBothId, OTP_ROLE);

  // Setup TOTP before custom auth flow (uses default browser flow)
  await setupTotpViaUI(realmName, 'user-totp-only', TEST_PASSWORD);
  await setupTotpViaUI(realmName, 'user-both-options', TEST_PASSWORD);

  await setupAlternativeFlow(realmName);
}

async function setupRequiredFlow(realmName: string) {
  const flowAlias = 'browser-with-email-otp';

  await keycloakAdmin.deleteAuthenticationFlow(realmName, flowAlias);
  await keycloakAdmin.createAuthenticationFlow(realmName, flowAlias, 'basic-flow');

  const cookieExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    flowAlias,
    'auth-cookie'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, flowAlias, cookieExecId, 'ALTERNATIVE');

  const formsSubflowId = await keycloakAdmin.addAuthenticationSubFlow(
    realmName,
    flowAlias,
    'email-otp-forms',
    'basic-flow'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, flowAlias, formsSubflowId, 'ALTERNATIVE');

  const userPassExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    'email-otp-forms',
    'auth-username-password-form'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, 'email-otp-forms', userPassExecId, 'REQUIRED');

  const emailOtpExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    'email-otp-forms',
    'email-otp-form'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, 'email-otp-forms', emailOtpExecId, 'REQUIRED');

  await keycloakAdmin.createAuthenticatorConfig(realmName, emailOtpExecId, 'email-otp-config', {
    'user-role': OTP_ROLE,
    'negate-user-role': 'false',
    'code-alphabet': '23456789ABCDEFGHJKLMNPQRSTUVWXYZ',
    'code-length': '6',
    'code-lifetime': '600',
  });

  await keycloakAdmin.bindBrowserFlow(realmName, flowAlias);
}

async function setupAlternativeFlow(realmName: string) {
  const flowAlias = 'browser-2fa-alternative';

  await keycloakAdmin.deleteAuthenticationFlow(realmName, flowAlias);
  await keycloakAdmin.copyAuthenticationFlow(realmName, 'browser', flowAlias);

  const executions = await keycloakAdmin.getAuthenticationExecutions(realmName, flowAlias);
  const formsExecution = executions.find(
    (e) => e.displayName?.toLowerCase().includes('forms') && e.authenticationFlow
  );

  if (!formsExecution) {
    throw new Error('Could not find forms subflow in browser flow');
  }

  const formsFlowAlias = formsExecution.displayName!;

  const conditionalSubflowId = await keycloakAdmin.addAuthenticationSubFlow(
    realmName,
    formsFlowAlias,
    '2FA Options',
    'basic-flow'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, formsFlowAlias, conditionalSubflowId, 'CONDITIONAL');

  const conditionExecutionId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    '2FA Options',
    'conditional-user-configured'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, '2FA Options', conditionExecutionId, 'REQUIRED');

  const emailOtpExecutionId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    '2FA Options',
    'email-otp-form'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, '2FA Options', emailOtpExecutionId, 'ALTERNATIVE');

  const totpExecutionId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    '2FA Options',
    'auth-otp-form'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, '2FA Options', totpExecutionId, 'ALTERNATIVE');

  await keycloakAdmin.createAuthenticatorConfig(realmName, emailOtpExecutionId, 'email-otp-config', {
    'user-role': OTP_ROLE,
    'negate-user-role': 'false',
    'code-alphabet': '23456789ABCDEFGHJKLMNPQRSTUVWXYZ',
    'code-length': '6',
    'code-lifetime': '600',
  });

  await keycloakAdmin.bindBrowserFlow(realmName, flowAlias);
}

async function setupIpTrustRealm() {
  const realmName = 'test-ip-trust';

  if (await keycloakAdmin.realmExists(realmName)) {
    await keycloakAdmin.deleteRealm(realmName);
  }

  await keycloakAdmin.createRealm(realmName);
  await keycloakAdmin.configureSmtp(realmName);
  await keycloakAdmin.createTestClient(realmName);

  await keycloakAdmin.createUser(
    realmName,
    'trust-user',
    'trust-user@test.local',
    TEST_PASSWORD
  );

  await setupIpTrustFlow(realmName);
}

async function setupDeviceTrustRealm() {
  const realmName = 'test-device-trust';

  if (await keycloakAdmin.realmExists(realmName)) {
    await keycloakAdmin.deleteRealm(realmName);
  }

  await keycloakAdmin.createRealm(realmName);
  await keycloakAdmin.configureSmtp(realmName);
  await keycloakAdmin.createTestClient(realmName);

  await keycloakAdmin.createUser(
    realmName,
    'trust-user',
    'trust-user@test.local',
    TEST_PASSWORD
  );

  await setupDeviceTrustFlow(realmName);
}

async function setupBothTrustRealm() {
  const realmName = 'test-both-trust';

  if (await keycloakAdmin.realmExists(realmName)) {
    await keycloakAdmin.deleteRealm(realmName);
  }

  await keycloakAdmin.createRealm(realmName);
  await keycloakAdmin.configureSmtp(realmName);
  await keycloakAdmin.createTestClient(realmName);

  await keycloakAdmin.createUser(
    realmName,
    'trust-user',
    'trust-user@test.local',
    TEST_PASSWORD
  );

  await setupBothTrustFlow(realmName);
}

async function setupIpTrustFlow(realmName: string) {
  const flowAlias = 'browser-ip-trust';

  await keycloakAdmin.deleteAuthenticationFlow(realmName, flowAlias);
  await keycloakAdmin.createAuthenticationFlow(realmName, flowAlias, 'basic-flow');

  const cookieExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    flowAlias,
    'auth-cookie'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, flowAlias, cookieExecId, 'ALTERNATIVE');

  const formsSubflowId = await keycloakAdmin.addAuthenticationSubFlow(
    realmName,
    flowAlias,
    'ip-trust-forms',
    'basic-flow'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, flowAlias, formsSubflowId, 'ALTERNATIVE');

  const userPassExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    'ip-trust-forms',
    'auth-username-password-form'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, 'ip-trust-forms', userPassExecId, 'REQUIRED');

  const emailOtpExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    'ip-trust-forms',
    'email-otp-form'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, 'ip-trust-forms', emailOtpExecId, 'REQUIRED');

  await keycloakAdmin.createAuthenticatorConfig(realmName, emailOtpExecId, 'email-otp-ip-trust-config', {
    'code-alphabet': '23456789ABCDEFGHJKLMNPQRSTUVWXYZ',
    'code-length': '6',
    'code-lifetime': '600',
    'ip-trust-enabled': 'true',
    'ip-trust-duration': '60',  // 60 minutes
    'device-trust-enabled': 'false',
  });

  await keycloakAdmin.bindBrowserFlow(realmName, flowAlias);
}

async function setupDeviceTrustFlow(realmName: string) {
  const flowAlias = 'browser-device-trust';

  await keycloakAdmin.deleteAuthenticationFlow(realmName, flowAlias);
  await keycloakAdmin.createAuthenticationFlow(realmName, flowAlias, 'basic-flow');

  const cookieExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    flowAlias,
    'auth-cookie'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, flowAlias, cookieExecId, 'ALTERNATIVE');

  const formsSubflowId = await keycloakAdmin.addAuthenticationSubFlow(
    realmName,
    flowAlias,
    'device-trust-forms',
    'basic-flow'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, flowAlias, formsSubflowId, 'ALTERNATIVE');

  const userPassExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    'device-trust-forms',
    'auth-username-password-form'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, 'device-trust-forms', userPassExecId, 'REQUIRED');

  const emailOtpExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    'device-trust-forms',
    'email-otp-form'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, 'device-trust-forms', emailOtpExecId, 'REQUIRED');

  await keycloakAdmin.createAuthenticatorConfig(realmName, emailOtpExecId, 'email-otp-device-trust-config', {
    'code-alphabet': '23456789ABCDEFGHJKLMNPQRSTUVWXYZ',
    'code-length': '6',
    'code-lifetime': '600',
    'ip-trust-enabled': 'false',
    'device-trust-enabled': 'true',
    'device-trust-duration': '30',  // 30 days
  });

  await keycloakAdmin.bindBrowserFlow(realmName, flowAlias);
}

async function setupBothTrustFlow(realmName: string) {
  const flowAlias = 'browser-both-trust';

  await keycloakAdmin.deleteAuthenticationFlow(realmName, flowAlias);
  await keycloakAdmin.createAuthenticationFlow(realmName, flowAlias, 'basic-flow');

  const cookieExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    flowAlias,
    'auth-cookie'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, flowAlias, cookieExecId, 'ALTERNATIVE');

  const formsSubflowId = await keycloakAdmin.addAuthenticationSubFlow(
    realmName,
    flowAlias,
    'both-trust-forms',
    'basic-flow'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, flowAlias, formsSubflowId, 'ALTERNATIVE');

  const userPassExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    'both-trust-forms',
    'auth-username-password-form'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, 'both-trust-forms', userPassExecId, 'REQUIRED');

  const emailOtpExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    'both-trust-forms',
    'email-otp-form'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, 'both-trust-forms', emailOtpExecId, 'REQUIRED');

  await keycloakAdmin.createAuthenticatorConfig(realmName, emailOtpExecId, 'email-otp-both-trust-config', {
    'code-alphabet': '23456789ABCDEFGHJKLMNPQRSTUVWXYZ',
    'code-length': '6',
    'code-lifetime': '600',
    'ip-trust-enabled': 'true',
    'ip-trust-duration': '60',  // 60 minutes
    'device-trust-enabled': 'true',
    'device-trust-duration': '30',  // 30 days
  });

  await keycloakAdmin.bindBrowserFlow(realmName, flowAlias);
}

async function setupTrustWithAlternativesRealm() {
  const realmName = 'test-trust-alternatives';

  if (await keycloakAdmin.realmExists(realmName)) {
    await keycloakAdmin.deleteRealm(realmName);
  }

  await keycloakAdmin.createRealm(realmName);
  await keycloakAdmin.configureSmtp(realmName);
  await keycloakAdmin.createTestClient(realmName);
  await keycloakAdmin.createRole(realmName, OTP_ROLE);

  // User with role and TOTP - has both options
  const userBothId = await keycloakAdmin.createUser(
    realmName,
    'user-both-options',
    'user-both-options@test.local',
    TEST_PASSWORD
  );
  await keycloakAdmin.assignRoleToUser(realmName, userBothId, OTP_ROLE);

  // User with role but no TOTP - only email OTP available
  const userEmailOnlyId = await keycloakAdmin.createUser(
    realmName,
    'user-email-only',
    'user-email-only@test.local',
    TEST_PASSWORD
  );
  await keycloakAdmin.assignRoleToUser(realmName, userEmailOnlyId, OTP_ROLE);

  // Setup TOTP for user-both-options before custom auth flow
  await setupTotpViaUI(realmName, 'user-both-options', TEST_PASSWORD);

  await setupTrustWithAlternativesFlow(realmName);
}

async function setupTrustWithAlternativesFlow(realmName: string) {
  const flowAlias = 'browser-trust-alternatives';

  await keycloakAdmin.deleteAuthenticationFlow(realmName, flowAlias);
  await keycloakAdmin.copyAuthenticationFlow(realmName, 'browser', flowAlias);

  const executions = await keycloakAdmin.getAuthenticationExecutions(realmName, flowAlias);
  const formsExecution = executions.find(
    (e) => e.displayName?.toLowerCase().includes('forms') && e.authenticationFlow
  );

  if (!formsExecution) {
    throw new Error('Could not find forms subflow in browser flow');
  }

  const formsFlowAlias = formsExecution.displayName!;

  const conditionalSubflowId = await keycloakAdmin.addAuthenticationSubFlow(
    realmName,
    formsFlowAlias,
    '2FA Options',
    'basic-flow'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, formsFlowAlias, conditionalSubflowId, 'CONDITIONAL');

  const conditionExecutionId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    '2FA Options',
    'conditional-user-configured'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, '2FA Options', conditionExecutionId, 'REQUIRED');

  const emailOtpExecutionId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    '2FA Options',
    'email-otp-form'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, '2FA Options', emailOtpExecutionId, 'ALTERNATIVE');

  const totpExecutionId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    '2FA Options',
    'auth-otp-form'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, '2FA Options', totpExecutionId, 'ALTERNATIVE');

  // Configure email OTP with IP trust enabled and trust-only-when-sole enabled (default)
  await keycloakAdmin.createAuthenticatorConfig(realmName, emailOtpExecutionId, 'email-otp-trust-alt-config', {
    'user-role': OTP_ROLE,
    'negate-user-role': 'false',
    'code-alphabet': '23456789ABCDEFGHJKLMNPQRSTUVWXYZ',
    'code-length': '6',
    'code-lifetime': '600',
    'ip-trust-enabled': 'true',
    'ip-trust-duration': '60',
    'device-trust-enabled': 'false',
    'trust-only-when-sole': 'true',  // This is the key setting being tested
  });

  await keycloakAdmin.bindBrowserFlow(realmName, flowAlias);
}

async function setupShortTtlRealm() {
  const realmName = 'test-short-ttl';

  if (await keycloakAdmin.realmExists(realmName)) {
    await keycloakAdmin.deleteRealm(realmName);
  }

  await keycloakAdmin.createRealm(realmName);
  await keycloakAdmin.configureSmtp(realmName);
  await keycloakAdmin.createTestClient(realmName);

  await keycloakAdmin.createUser(
    realmName,
    'ttl-user',
    'ttl-user@test.local',
    TEST_PASSWORD
  );

  await setupShortTtlFlow(realmName);
}

async function setupShortTtlFlow(realmName: string) {
  const flowAlias = 'browser-short-ttl';

  await keycloakAdmin.deleteAuthenticationFlow(realmName, flowAlias);
  await keycloakAdmin.createAuthenticationFlow(realmName, flowAlias, 'basic-flow');

  const cookieExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    flowAlias,
    'auth-cookie'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, flowAlias, cookieExecId, 'ALTERNATIVE');

  const formsSubflowId = await keycloakAdmin.addAuthenticationSubFlow(
    realmName,
    flowAlias,
    'short-ttl-forms',
    'basic-flow'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, flowAlias, formsSubflowId, 'ALTERNATIVE');

  const userPassExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    'short-ttl-forms',
    'auth-username-password-form'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, 'short-ttl-forms', userPassExecId, 'REQUIRED');

  const emailOtpExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    'short-ttl-forms',
    'email-otp-form'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, 'short-ttl-forms', emailOtpExecId, 'REQUIRED');

  await keycloakAdmin.createAuthenticatorConfig(realmName, emailOtpExecId, 'email-otp-short-ttl-config', {
    'code-alphabet': '23456789ABCDEFGHJKLMNPQRSTUVWXYZ',
    'code-length': '6',
    'code-lifetime': '3',  // 3 seconds - very short for testing expiration
  });

  await keycloakAdmin.bindBrowserFlow(realmName, flowAlias);
}

async function setupI18nRealm() {
  const realmName = 'test-i18n';

  if (await keycloakAdmin.realmExists(realmName)) {
    await keycloakAdmin.deleteRealm(realmName);
  }

  await keycloakAdmin.createRealm(realmName);
  await keycloakAdmin.configureSmtp(realmName);
  await keycloakAdmin.createTestClient(realmName);

  // Enable internationalization with multiple locales for plural testing
  await keycloakAdmin.enableInternationalization(realmName, [
    'en', 'ar', 'ru', 'pl', 'cs', 'sl', 'uk', 'de', 'fr'
  ], 'en');

  await keycloakAdmin.createUser(
    realmName,
    'i18n-user',
    'i18n-user@test.local',
    TEST_PASSWORD
  );

  await setupI18nFlow(realmName);
}

async function setupI18nFlow(realmName: string) {
  const flowAlias = 'browser-i18n';

  await keycloakAdmin.deleteAuthenticationFlow(realmName, flowAlias);
  await keycloakAdmin.createAuthenticationFlow(realmName, flowAlias, 'basic-flow');

  const cookieExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    flowAlias,
    'auth-cookie'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, flowAlias, cookieExecId, 'ALTERNATIVE');

  const formsSubflowId = await keycloakAdmin.addAuthenticationSubFlow(
    realmName,
    flowAlias,
    'i18n-forms',
    'basic-flow'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, flowAlias, formsSubflowId, 'ALTERNATIVE');

  const userPassExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    'i18n-forms',
    'auth-username-password-form'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, 'i18n-forms', userPassExecId, 'REQUIRED');

  const emailOtpExecId = await keycloakAdmin.addAuthenticationExecution(
    realmName,
    'i18n-forms',
    'email-otp-form'
  );
  await keycloakAdmin.updateAuthenticationExecution(realmName, 'i18n-forms', emailOtpExecId, 'REQUIRED');

  // Configure with 3 days device trust (tests "few" plural in Slavic languages)
  await keycloakAdmin.createAuthenticatorConfig(realmName, emailOtpExecId, 'email-otp-i18n-config', {
    'code-alphabet': '23456789ABCDEFGHJKLMNPQRSTUVWXYZ',
    'code-length': '6',
    'code-lifetime': '600',
    'ip-trust-enabled': 'false',
    'device-trust-enabled': 'true',
    'device-trust-duration': '3',  // 3 days - tests "few" plural form
  });

  await keycloakAdmin.bindBrowserFlow(realmName, flowAlias);
}

export { TEST_PASSWORD, OTP_ROLE };
