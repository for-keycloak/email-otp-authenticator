package ch.jacem.for_keycloak.email_otp_authenticator.trust;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

public class TrustStoreSpi implements Spi {

    public static final String NAME = "email-otp-trust-store";

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return TrustStore.class;
    }

    @Override
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return TrustStoreFactory.class;
    }
}
