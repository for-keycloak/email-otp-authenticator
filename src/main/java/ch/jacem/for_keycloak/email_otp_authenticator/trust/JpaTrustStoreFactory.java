package ch.jacem.for_keycloak.email_otp_authenticator.trust;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.timer.TimerProvider;

public class JpaTrustStoreFactory implements TrustStoreFactory {

    private static final Logger logger = Logger.getLogger(JpaTrustStoreFactory.class);

    public static final String PROVIDER_ID = "jpa";

    // Default cleanup interval: 1 hour (in milliseconds)
    private static final long DEFAULT_CLEANUP_INTERVAL_MS = 60 * 60 * 1000L;

    private long cleanupIntervalMs = DEFAULT_CLEANUP_INTERVAL_MS;

    @Override
    public TrustStore create(KeycloakSession session) {
        return new JpaTrustStore(session);
    }

    @Override
    public void init(Config.Scope config) {
        // Allow configuration of cleanup interval via SPI config
        if (config != null) {
            Long intervalMinutes = config.getLong("cleanup-interval-minutes");
            if (intervalMinutes != null) {
                this.cleanupIntervalMs = intervalMinutes * 60 * 1000L;
                logger.infof("Email OTP trust cleanup interval set to %d minutes", intervalMinutes);
            }
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Schedule the cleanup task
        KeycloakSession session = factory.create();
        try {
            TimerProvider timer = session.getProvider(TimerProvider.class);
            if (timer != null) {
                timer.scheduleTask(new TrustCleanupTask(), cleanupIntervalMs, "EmailOtpTrustCleanup");
                logger.infof("Scheduled email OTP trust cleanup task to run every %d ms", cleanupIntervalMs);
            } else {
                logger.warn("TimerProvider not available, cleanup task not scheduled");
            }
        } finally {
            session.close();
        }
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
