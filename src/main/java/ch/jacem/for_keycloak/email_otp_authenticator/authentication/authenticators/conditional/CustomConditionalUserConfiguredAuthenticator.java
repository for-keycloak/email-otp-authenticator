package ch.jacem.for_keycloak.email_otp_authenticator.authentication.authenticators.conditional;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticator;
import org.keycloak.authentication.authenticators.conditional.ConditionalUserConfiguredAuthenticator;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticatorConfigModel;

public class CustomConditionalUserConfiguredAuthenticator extends ConditionalUserConfiguredAuthenticator{
    public static final CustomConditionalUserConfiguredAuthenticator SINGLETON = new CustomConditionalUserConfiguredAuthenticator();

    @Override
    public boolean matchCondition(AuthenticationFlowContext context) {
        return matchConditionInFlow(context, context.getExecution().getParentFlow());
    }

    private boolean matchConditionInFlow(AuthenticationFlowContext context, String flowId) {
        List<AuthenticationExecutionModel> requiredExecutions = new LinkedList<>();
        List<AuthenticationExecutionModel> alternativeExecutions = new LinkedList<>();
        context.getRealm().getAuthenticationExecutionsStream(flowId)
                //Check if the execution's authenticator is a conditional authenticator, as they must not be evaluated here.
                .filter(e -> !isConditionalExecution(context, e))
                .filter(e -> !Objects.equals(context.getExecution().getId(), e.getId()) && !e.isAuthenticatorFlow())
                .forEachOrdered(e -> {
                    if (e.isRequired()) {
                        requiredExecutions.add(e);
                    } else if (e.isAlternative()) {
                        alternativeExecutions.add(e);
                    }
                });
        if (!requiredExecutions.isEmpty()) {
            return requiredExecutions.stream().allMatch(e -> isConfiguredFor(e, context));
        } else  if (!alternativeExecutions.isEmpty()) {
            return alternativeExecutions.stream().anyMatch(e -> isConfiguredFor(e, context));
        }
        return true;
    }

    private boolean isConditionalExecution(AuthenticationFlowContext context, AuthenticationExecutionModel e) {
        AuthenticatorFactory factory = (AuthenticatorFactory) context.getSession().getKeycloakSessionFactory()
                .getProviderFactory(Authenticator.class, e.getAuthenticator());
        if (factory != null) {
            Authenticator auth = factory.create(context.getSession());
            return (auth instanceof ConditionalAuthenticator);
        }
        return false;
    }

    private boolean isConfiguredFor(AuthenticationExecutionModel model, AuthenticationFlowContext context) {
        // call the super method if the model does not implement the interface
        if (model.isAuthenticatorFlow()) {
            return matchConditionInFlow(context, model.getId());
        }

        AuthenticatorFactory factory = (AuthenticatorFactory) context.getSession().getKeycloakSessionFactory().getProviderFactory(Authenticator.class, model.getAuthenticator());
        Authenticator authenticator = factory.create(context.getSession());

        if (authenticator instanceof AcceptsFullContextInConfiguredFor) {
            AuthenticatorConfigModel config = context.getRealm().getAuthenticatorConfigById(model.getAuthenticatorConfig());

            return ((AcceptsFullContextInConfiguredFor) authenticator).configuredFor(context, config);
        }

        // Guard against null user - can happen before authentication completes
        if (context.getUser() == null) {
            return false;
        }

        return authenticator.configuredFor(context.getSession(), context.getRealm(), context.getUser());
    }
}
