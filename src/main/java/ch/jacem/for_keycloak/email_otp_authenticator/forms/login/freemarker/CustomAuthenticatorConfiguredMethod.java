package ch.jacem.for_keycloak.email_otp_authenticator.forms.login.freemarker;

import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;

import ch.jacem.for_keycloak.email_otp_authenticator.authentication.authenticators.conditional.AcceptsFullContextInConfiguredFor;

import java.util.List;

public class CustomAuthenticatorConfiguredMethod implements TemplateMethodModelEx {
    private final AuthenticationFlowContext context;
    private final AuthenticatorConfigModel config;

    public CustomAuthenticatorConfiguredMethod(AuthenticationFlowContext context, AuthenticatorConfigModel config) {
        this.context = context;
        this.config = config;
    }

    @Override
    public Object exec(List list) throws TemplateModelException {
        String providerId = list.get(0).toString();
        KeycloakSession session = this.context.getSession();
        Authenticator authenticator = session.getProvider(Authenticator.class, providerId);

        if (authenticator instanceof AcceptsFullContextInConfiguredFor) {
            return ((AcceptsFullContextInConfiguredFor) authenticator).configuredFor(context, config);
        }

        return authenticator.configuredFor(session, this.context.getRealm(), this.context.getUser());
    }
}
