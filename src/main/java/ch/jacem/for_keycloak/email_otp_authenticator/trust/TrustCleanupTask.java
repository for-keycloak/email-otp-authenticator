package ch.jacem.for_keycloak.email_otp_authenticator.trust;

import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.timer.ScheduledTask;

/**
 * Scheduled task to clean up expired trust entries.
 */
public class TrustCleanupTask implements ScheduledTask {

    private static final Logger logger = Logger.getLogger(TrustCleanupTask.class);

    @Override
    public void run(KeycloakSession session) {
        logger.debug("Running email OTP trust cleanup task");
        try {
            TrustStore trustStore = session.getProvider(TrustStore.class);
            if (trustStore != null) {
                int cleaned = trustStore.cleanupExpired();
                if (cleaned > 0) {
                    logger.infof("Email OTP trust cleanup: removed %d expired entries", cleaned);
                }
            } else {
                logger.warn("TrustStore provider not available for cleanup");
            }
        } catch (Exception e) {
            logger.error("Error during email OTP trust cleanup", e);
        }
    }
}
