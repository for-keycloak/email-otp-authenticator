package ch.jacem.for_keycloak.email_otp_authenticator.jpa;

import org.keycloak.Config;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class EmailOtpJpaEntityProviderFactory implements JpaEntityProviderFactory {

    public static final String PROVIDER_ID = "email-otp-jpa-entity-provider";

    @Override
    public JpaEntityProvider create(KeycloakSession session) {
        return new EmailOtpJpaEntityProvider();
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
