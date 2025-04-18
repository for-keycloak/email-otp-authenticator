package ch.jacem.for_keycloak.email_otp_authenticator.authentication.authenticators.conditional;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.models.AuthenticatorConfigModel;

public interface AcceptsFullContextInConfiguredFor {
    /**
     * Is this authenticator configured for this user.
     *
     * @param context
     * @param config
     * @return
     */
    boolean configuredFor(AuthenticationFlowContext context, AuthenticatorConfigModel config);
}
