const KEYCLOAK_URL = process.env.KEYCLOAK_URL || 'http://localhost:8080';
const ADMIN_USERNAME = 'admin';
const ADMIN_PASSWORD = 'admin';

interface TokenResponse {
  access_token: string;
  expires_in: number;
  token_type: string;
}

interface AuthenticationExecutionInfo {
  id: string;
  requirement: string;
  displayName: string;
  alias?: string;
  authenticationFlow?: boolean;
  providerId?: string;
  level: number;
  index: number;
}

export class KeycloakAdmin {
  private accessToken: string | null = null;

  async getAccessToken(): Promise<string> {
    if (this.accessToken) {
      return this.accessToken;
    }

    const response = await fetch(
      `${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams({
          grant_type: 'password',
          client_id: 'admin-cli',
          username: ADMIN_USERNAME,
          password: ADMIN_PASSWORD,
        }),
      }
    );

    if (!response.ok) {
      throw new Error(`Failed to get admin token: ${response.status}`);
    }

    const data: TokenResponse = await response.json();
    this.accessToken = data.access_token;
    return this.accessToken;
  }

  private async request(
    path: string,
    options: RequestInit = {}
  ): Promise<Response> {
    const token = await this.getAccessToken();
    const response = await fetch(`${KEYCLOAK_URL}/admin/realms${path}`, {
      ...options,
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        ...options.headers,
      },
    });
    return response;
  }

  async realmExists(realmName: string): Promise<boolean> {
    const response = await this.request(`/${realmName}`);
    return response.ok;
  }

  async getRealmConfig(realmName: string): Promise<{ browserFlow: string }> {
    const response = await this.request(`/${realmName}`);
    if (!response.ok) {
      throw new Error(`Failed to get realm config: ${response.status}`);
    }
    return response.json();
  }

  async deleteRealm(realmName: string): Promise<void> {
    await this.request(`/${realmName}`, { method: 'DELETE' });
  }

  async createRealm(realmName: string): Promise<void> {
    const response = await this.request('', {
      method: 'POST',
      body: JSON.stringify({
        realm: realmName,
        enabled: true,
        registrationAllowed: false,
        loginWithEmailAllowed: true,
        duplicateEmailsAllowed: false,
        resetPasswordAllowed: false,
        editUsernameAllowed: false,
        bruteForceProtected: false,
        // Disable default required actions that could interfere with tests
        requiredActions: [],
      }),
    });

    if (!response.ok && response.status !== 409) {
      throw new Error(`Failed to create realm: ${response.status}`);
    }

    // Disable VERIFY_PROFILE required action
    await this.disableRequiredAction(realmName, 'VERIFY_PROFILE');
  }

  async disableRequiredAction(realmName: string, actionAlias: string): Promise<void> {
    const getResponse = await this.request(
      `/${realmName}/authentication/required-actions/${actionAlias}`
    );

    if (!getResponse.ok) {
      return;
    }

    const action = await getResponse.json();

    await this.request(
      `/${realmName}/authentication/required-actions/${actionAlias}`,
      {
        method: 'PUT',
        body: JSON.stringify({
          ...action,
          enabled: false,
          defaultAction: false,
        }),
      }
    );
  }

  async addUserRequiredAction(
    realmName: string,
    userId: string,
    actionAlias: string
  ): Promise<void> {
    // Get current user
    const getResponse = await this.request(`/${realmName}/users/${userId}`);
    if (!getResponse.ok) {
      throw new Error(`Failed to get user: ${getResponse.status}`);
    }

    const user = await getResponse.json();

    // Add the required action
    const requiredActions = user.requiredActions || [];
    if (!requiredActions.includes(actionAlias)) {
      requiredActions.push(actionAlias);
    }

    // Update user
    const response = await this.request(`/${realmName}/users/${userId}`, {
      method: 'PUT',
      body: JSON.stringify({
        ...user,
        requiredActions,
      }),
    });

    if (!response.ok) {
      throw new Error(`Failed to add required action: ${response.status}`);
    }
  }

  async listRequiredActions(realmName: string): Promise<Array<{alias: string, name: string, enabled: boolean}>> {
    const response = await this.request(`/${realmName}/authentication/required-actions`);
    if (!response.ok) {
      throw new Error(`Failed to list required actions: ${response.status}`);
    }
    return response.json();
  }

  async registerRequiredAction(realmName: string, providerId: string): Promise<void> {
    await this.request(
      `/${realmName}/authentication/register-required-action`,
      {
        method: 'POST',
        body: JSON.stringify({
          providerId: providerId,
          name: providerId,
        }),
      }
    );
  }

  async enableRequiredAction(realmName: string, actionAlias: string): Promise<void> {
    let allActions = await this.listRequiredActions(realmName);

    if (!allActions.find(a => a.alias === actionAlias)) {
      await this.registerRequiredAction(realmName, actionAlias);
    }

    const getResponse = await this.request(
      `/${realmName}/authentication/required-actions/${actionAlias}`
    );

    if (!getResponse.ok) {
      return;
    }

    const action = await getResponse.json();

    await this.request(
      `/${realmName}/authentication/required-actions/${actionAlias}`,
      {
        method: 'PUT',
        body: JSON.stringify({
          ...action,
          enabled: true,
        }),
      }
    );
  }

  async removeUserRequiredAction(
    realmName: string,
    userId: string,
    actionAlias: string
  ): Promise<void> {
    // Get current user
    const getResponse = await this.request(`/${realmName}/users/${userId}`);
    if (!getResponse.ok) {
      throw new Error(`Failed to get user: ${getResponse.status}`);
    }

    const user = await getResponse.json();

    // Remove the required action
    const requiredActions = (user.requiredActions || []).filter(
      (a: string) => a !== actionAlias
    );

    // Update user
    const response = await this.request(`/${realmName}/users/${userId}`, {
      method: 'PUT',
      body: JSON.stringify({
        ...user,
        requiredActions,
      }),
    });

    if (!response.ok) {
      throw new Error(`Failed to remove required action: ${response.status}`);
    }
  }

  async configureSmtp(realmName: string): Promise<void> {
    const response = await this.request(`/${realmName}`, {
      method: 'PUT',
      body: JSON.stringify({
        realm: realmName,
        smtpServer: {
          host: 'mailpit',
          port: '1025',
          from: 'keycloak@test.local',
          fromDisplayName: 'Keycloak Test',
        },
      }),
    });

    if (!response.ok) {
      throw new Error(`Failed to configure SMTP: ${response.status}`);
    }
  }

  async createRole(realmName: string, roleName: string): Promise<void> {
    const response = await this.request(`/${realmName}/roles`, {
      method: 'POST',
      body: JSON.stringify({
        name: roleName,
      }),
    });

    if (!response.ok && response.status !== 409) {
      throw new Error(`Failed to create role: ${response.status}`);
    }
  }

  async getRoleByName(
    realmName: string,
    roleName: string
  ): Promise<{ id: string; name: string }> {
    const response = await this.request(`/${realmName}/roles/${roleName}`);
    if (!response.ok) {
      throw new Error(`Failed to get role: ${response.status}`);
    }
    return response.json();
  }

  async createUser(
    realmName: string,
    username: string,
    email: string,
    password: string
  ): Promise<string> {
    const createResponse = await this.request(`/${realmName}/users`, {
      method: 'POST',
      body: JSON.stringify({
        username,
        email,
        emailVerified: true,
        enabled: true,
        credentials: [
          {
            type: 'password',
            value: password,
            temporary: false,
          },
        ],
      }),
    });

    if (!createResponse.ok && createResponse.status !== 409) {
      throw new Error(`Failed to create user: ${createResponse.status}`);
    }

    // Get user ID
    const usersResponse = await this.request(
      `/${realmName}/users?username=${username}&exact=true`
    );
    const users = await usersResponse.json();
    return users[0].id;
  }

  async assignRoleToUser(
    realmName: string,
    userId: string,
    roleName: string
  ): Promise<void> {
    const role = await this.getRoleByName(realmName, roleName);
    const response = await this.request(
      `/${realmName}/users/${userId}/role-mappings/realm`,
      {
        method: 'POST',
        body: JSON.stringify([role]),
      }
    );

    if (!response.ok) {
      throw new Error(`Failed to assign role: ${response.status}`);
    }
  }

  async createAuthenticationFlow(
    realmName: string,
    flowAlias: string,
    providerId: string = 'basic-flow'
  ): Promise<void> {
    const response = await this.request(
      `/${realmName}/authentication/flows`,
      {
        method: 'POST',
        body: JSON.stringify({
          alias: flowAlias,
          description: '',
          providerId: providerId,
          topLevel: true,
          builtIn: false,
        }),
      }
    );

    if (!response.ok && response.status !== 409) {
      const text = await response.text();
      throw new Error(`Failed to create flow: ${response.status} - ${text}`);
    }
  }

  async copyAuthenticationFlow(
    realmName: string,
    sourceFlowAlias: string,
    newFlowAlias: string
  ): Promise<void> {
    const response = await this.request(
      `/${realmName}/authentication/flows/${sourceFlowAlias}/copy`,
      {
        method: 'POST',
        body: JSON.stringify({
          newName: newFlowAlias,
        }),
      }
    );

    if (!response.ok && response.status !== 409) {
      const text = await response.text();
      throw new Error(`Failed to copy flow: ${response.status} - ${text}`);
    }
  }

  async getAuthenticationExecutions(
    realmName: string,
    flowAlias: string
  ): Promise<AuthenticationExecutionInfo[]> {
    const encodedAlias = encodeURIComponent(flowAlias);
    const response = await this.request(
      `/${realmName}/authentication/flows/${encodedAlias}/executions`
    );
    if (!response.ok) {
      throw new Error(`Failed to get executions: ${response.status}`);
    }
    return response.json();
  }

  async addAuthenticationExecution(
    realmName: string,
    flowAlias: string,
    providerId: string
  ): Promise<string> {
    const encodedAlias = encodeURIComponent(flowAlias);
    const response = await this.request(
      `/${realmName}/authentication/flows/${encodedAlias}/executions/execution`,
      {
        method: 'POST',
        body: JSON.stringify({
          provider: providerId,
        }),
      }
    );

    if (!response.ok) {
      const text = await response.text();
      throw new Error(`Failed to add execution: ${response.status} - ${text}`);
    }

    // Get the execution ID from Location header or fetch executions
    const executions = await this.getAuthenticationExecutions(
      realmName,
      flowAlias
    );
    const execution = executions.find((e) => e.providerId === providerId);
    if (!execution) {
      throw new Error(`Could not find execution for provider: ${providerId}`);
    }
    return execution.id;
  }

  async addAuthenticationSubFlow(
    realmName: string,
    parentFlowAlias: string,
    subFlowAlias: string,
    providerId: string = 'basic-flow'
  ): Promise<string> {
    const encodedParentAlias = encodeURIComponent(parentFlowAlias);
    const response = await this.request(
      `/${realmName}/authentication/flows/${encodedParentAlias}/executions/flow`,
      {
        method: 'POST',
        body: JSON.stringify({
          alias: subFlowAlias,
          type: providerId,
          provider: 'registration-page-form',
          description: '',
        }),
      }
    );

    if (!response.ok) {
      const text = await response.text();
      throw new Error(`Failed to add subflow: ${response.status} - ${text}`);
    }

    const executions = await this.getAuthenticationExecutions(
      realmName,
      parentFlowAlias
    );
    const execution = executions.find((e) => e.displayName === subFlowAlias);
    if (!execution) {
      throw new Error(`Could not find subflow: ${subFlowAlias}`);
    }
    return execution.id;
  }

  async updateAuthenticationExecution(
    realmName: string,
    flowAlias: string,
    executionId: string,
    requirement: 'REQUIRED' | 'ALTERNATIVE' | 'DISABLED' | 'CONDITIONAL'
  ): Promise<void> {
    const encodedAlias = encodeURIComponent(flowAlias);
    const response = await this.request(
      `/${realmName}/authentication/flows/${encodedAlias}/executions`,
      {
        method: 'PUT',
        body: JSON.stringify({
          id: executionId,
          requirement,
        }),
      }
    );

    if (!response.ok) {
      const text = await response.text();
      throw new Error(
        `Failed to update execution: ${response.status} - ${text}`
      );
    }
  }

  async lowerExecutionPriority(realmName: string, executionId: string): Promise<void> {
    const response = await this.request(
      `/${realmName}/authentication/executions/${executionId}/lower-priority`,
      { method: 'POST' }
    );
    if (!response.ok) {
      const text = await response.text();
      throw new Error(`Failed to lower execution priority: ${response.status} - ${text}`);
    }
  }

  async raiseExecutionPriority(realmName: string, executionId: string): Promise<void> {
    const response = await this.request(
      `/${realmName}/authentication/executions/${executionId}/raise-priority`,
      { method: 'POST' }
    );
    if (!response.ok) {
      const text = await response.text();
      throw new Error(`Failed to raise execution priority: ${response.status} - ${text}`);
    }
  }

  async createAuthenticatorConfig(
    realmName: string,
    executionId: string,
    alias: string,
    config: Record<string, string>
  ): Promise<void> {
    const response = await this.request(
      `/${realmName}/authentication/executions/${executionId}/config`,
      {
        method: 'POST',
        body: JSON.stringify({
          alias,
          config,
        }),
      }
    );

    if (!response.ok) {
      const text = await response.text();
      throw new Error(`Failed to create config: ${response.status} - ${text}`);
    }
  }

  async bindBrowserFlow(realmName: string, flowAlias: string): Promise<void> {
    const response = await this.request(`/${realmName}`, {
      method: 'PUT',
      body: JSON.stringify({
        realm: realmName,
        browserFlow: flowAlias,
      }),
    });

    if (!response.ok) {
      throw new Error(`Failed to bind browser flow: ${response.status}`);
    }
  }

  async setupTotpForUser(
    realmName: string,
    userId: string,
    totpSecret: string
  ): Promise<void> {
    const credentialId = crypto.randomUUID();

    const response = await this.request(
      `/${realmName}/users/${userId}/credentials/${credentialId}`,
      {
        method: 'PUT',
        body: JSON.stringify({
          id: credentialId,
          type: 'otp',
          userLabel: 'Authenticator',
          credentialData: JSON.stringify({
            subType: 'totp',
            period: 30,
            digits: 6,
            algorithm: 'HmacSHA1',
          }),
          secretData: JSON.stringify({
            value: totpSecret,
          }),
        }),
      }
    );

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`Failed to setup TOTP for user ${userId}: ${response.status} - ${errorText}`);
    }
  }

  async deleteAuthenticationFlow(
    realmName: string,
    flowAlias: string
  ): Promise<void> {
    // First get the flow ID
    const response = await this.request(
      `/${realmName}/authentication/flows`
    );
    if (!response.ok) {
      return;
    }

    const flows = await response.json();
    const flow = flows.find((f: { alias: string }) => f.alias === flowAlias);
    if (!flow) {
      return;
    }

    await this.request(`/${realmName}/authentication/flows/${flow.id}`, {
      method: 'DELETE',
    });
  }

  async getClientByClientId(
    realmName: string,
    clientId: string
  ): Promise<{ id: string; clientId: string } | null> {
    const response = await this.request(
      `/${realmName}/clients?clientId=${clientId}`
    );
    if (!response.ok) {
      return null;
    }
    const clients = await response.json();
    return clients.length > 0 ? clients[0] : null;
  }

  async createTestClient(realmName: string): Promise<void> {
    const existingClient = await this.getClientByClientId(realmName, 'test-client');
    if (existingClient) {
      return;
    }

    const response = await this.request(`/${realmName}/clients`, {
      method: 'POST',
      body: JSON.stringify({
        clientId: 'test-client',
        enabled: true,
        publicClient: true,
        redirectUris: ['http://localhost:8080/*', 'http://keycloak:8080/*', '*'],
        webOrigins: ['http://localhost:8080', 'http://keycloak:8080', '*'],
        directAccessGrantsEnabled: true,
        standardFlowEnabled: true,
      }),
    });

    if (!response.ok && response.status !== 409) {
      throw new Error(`Failed to create test client: ${response.status}`);
    }
  }
}

export const keycloakAdmin = new KeycloakAdmin();
