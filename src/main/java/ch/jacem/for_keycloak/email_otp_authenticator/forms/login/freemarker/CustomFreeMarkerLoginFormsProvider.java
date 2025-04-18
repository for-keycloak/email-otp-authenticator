package ch.jacem.for_keycloak.email_otp_authenticator.forms.login.freemarker;

import org.keycloak.forms.login.LoginFormsPages;
import org.keycloak.forms.login.freemarker.FreeMarkerLoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.theme.Theme;

import jakarta.ws.rs.core.UriBuilder;

import java.util.Locale;
import java.util.Properties;

public class CustomFreeMarkerLoginFormsProvider extends FreeMarkerLoginFormsProvider {
    public CustomFreeMarkerLoginFormsProvider(KeycloakSession session) {
        super(session);
    }

    @Override
    protected void createCommonAttributes(Theme theme, Locale locale, Properties messagesBundle, UriBuilder baseUriBuilder, LoginFormsPages page) {
        super.createCommonAttributes(theme, locale, messagesBundle, baseUriBuilder, page);

        if (attributes.containsKey("authenticatorConfigured") && null != this.context && null != this.realm && null != this.execution) {
            // Get configuration
            AuthenticationExecutionModel executionModel = this.realm.getAuthenticationExecutionById(this.execution);
            if (null == executionModel) return;
            AuthenticatorConfigModel configModel = this.realm.getAuthenticatorConfigById(executionModel.getAuthenticatorConfig());
            if (null == configModel) return;

            attributes.put("authenticatorConfigured", new CustomAuthenticatorConfiguredMethod(this.context, configModel));
        }
    }
}
