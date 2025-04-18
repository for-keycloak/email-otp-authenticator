package ch.jacem.for_keycloak.email_otp_authenticator.authentication.authenticators.conditional;

import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticator;
import org.keycloak.authentication.authenticators.conditional.ConditionalUserConfiguredAuthenticatorFactory;

public class CustomConditionalUserConfiguredAuthenticatorFactory extends ConditionalUserConfiguredAuthenticatorFactory {
    @Override
    public ConditionalAuthenticator getSingleton() {
        return CustomConditionalUserConfiguredAuthenticator.SINGLETON;
    }
}
