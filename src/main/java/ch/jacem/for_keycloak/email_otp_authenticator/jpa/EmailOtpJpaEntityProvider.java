package ch.jacem.for_keycloak.email_otp_authenticator.jpa;

import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;

import java.util.Arrays;
import java.util.List;

public class EmailOtpJpaEntityProvider implements JpaEntityProvider {

    @Override
    public List<Class<?>> getEntities() {
        return Arrays.asList(
            TrustedIpEntity.class,
            TrustedDeviceEntity.class
        );
    }

    @Override
    public String getChangelogLocation() {
        return "META-INF/email-otp-changelog.xml";
    }

    @Override
    public String getFactoryId() {
        return EmailOtpJpaEntityProviderFactory.PROVIDER_ID;
    }

    @Override
    public void close() {
    }
}
