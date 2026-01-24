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

export { TEST_PASSWORD, OTP_ROLE };
