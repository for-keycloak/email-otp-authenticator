package ch.jacem.for_keycloak.email_otp_authenticator.forms.login.freemarker;

import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.forms.login.freemarker.FreeMarkerLoginFormsProviderFactory;
import org.keycloak.models.KeycloakSession;

public class CustomFreeMarkerLoginFormsProviderFactory extends FreeMarkerLoginFormsProviderFactory {
    @Override
    public LoginFormsProvider create(KeycloakSession session) {
        return new CustomFreeMarkerLoginFormsProvider(session);
    }
}
