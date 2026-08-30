package ch.jacem.for_keycloak.email_otp_authenticator.authentication.authenticators.conditional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

@DisplayName("CustomConditionalUserConfiguredAuthenticator")
class CustomConditionalUserConfiguredAuthenticatorTest {

    private static final String FLOW_ID = "flow-id";
    private static final String CONDITION_EXECUTION_ID = "condition-execution-id";
    private static final String TARGET_EXECUTION_ID = "target-execution-id";
    private static final String TARGET_PROVIDER_ID = "some-authenticator";

    private AuthenticationFlowContext context;
    private RealmModel realm;
    private KeycloakSession session;
    private Authenticator targetAuthenticator;
    private AuthenticationExecutionModel targetExecution;

    @BeforeEach
    void setUp() {
        context = mock(AuthenticationFlowContext.class);
        realm = mock(RealmModel.class);
        session = mock(KeycloakSession.class);
        KeycloakSessionFactory sessionFactory = mock(KeycloakSessionFactory.class);
        AuthenticatorFactory targetFactory = mock(AuthenticatorFactory.class);
        targetAuthenticator = mock(Authenticator.class);

        AuthenticationExecutionModel conditionExecution = mock(AuthenticationExecutionModel.class);
        when(conditionExecution.getParentFlow()).thenReturn(FLOW_ID);
        when(conditionExecution.getId()).thenReturn(CONDITION_EXECUTION_ID);

        targetExecution = mock(AuthenticationExecutionModel.class);
        when(targetExecution.getId()).thenReturn(TARGET_EXECUTION_ID);
        when(targetExecution.isAuthenticatorFlow()).thenReturn(false);
        when(targetExecution.getAuthenticator()).thenReturn(TARGET_PROVIDER_ID);
        when(targetExecution.isRequired()).thenReturn(false);
        when(targetExecution.isAlternative()).thenReturn(true);

        when(context.getExecution()).thenReturn(conditionExecution);
        when(context.getRealm()).thenReturn(realm);
        when(context.getSession()).thenReturn(session);
        when(realm.getAuthenticationExecutionsStream(FLOW_ID))
                .thenAnswer(invocation -> Stream.of(targetExecution));
        when(session.getKeycloakSessionFactory()).thenReturn(sessionFactory);
        when(sessionFactory.getProviderFactory(eq(Authenticator.class), eq(TARGET_PROVIDER_ID)))
                .thenReturn(targetFactory);
        when(targetFactory.create(session)).thenReturn(targetAuthenticator);
    }

    @Test
    @DisplayName("matches when no user is set yet but the authenticator does not require one")
    void matchesWithoutUserWhenAuthenticatorDoesNotRequireUser() {
        // e.g. the Organization Identity-First Login authenticator: requiresUser() == false,
        // configuredFor() == realm.isOrganizationsEnabled(). It is designed to be evaluated
        // before any user is identified.
        when(context.getUser()).thenReturn(null);
        when(targetAuthenticator.requiresUser()).thenReturn(false);
        when(targetAuthenticator.configuredFor(eq(session), eq(realm), any())).thenReturn(true);

        assertTrue(CustomConditionalUserConfiguredAuthenticator.SINGLETON.matchCondition(context));
    }

    @Test
    @DisplayName("does not match when no user is set yet and the authenticator requires one")
    void doesNotMatchWithoutUserWhenAuthenticatorRequiresUser() {
        when(context.getUser()).thenReturn(null);
        when(targetAuthenticator.requiresUser()).thenReturn(true);

        assertFalse(CustomConditionalUserConfiguredAuthenticator.SINGLETON.matchCondition(context));
        verify(targetAuthenticator, never()).configuredFor(any(), any(), any());
    }

    @Test
    @DisplayName("delegates to configuredFor when a user is set")
    void delegatesToConfiguredForWhenUserIsSet() {
        UserModel user = mock(UserModel.class);
        when(context.getUser()).thenReturn(user);
        when(targetAuthenticator.requiresUser()).thenReturn(true);
        when(targetAuthenticator.configuredFor(session, realm, user)).thenReturn(true);

        assertTrue(CustomConditionalUserConfiguredAuthenticator.SINGLETON.matchCondition(context));
    }
}
