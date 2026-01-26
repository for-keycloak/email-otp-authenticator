package ch.jacem.for_keycloak.email_otp_authenticator.trust;

import ch.jacem.for_keycloak.email_otp_authenticator.jpa.TrustedDeviceEntity;
import ch.jacem.for_keycloak.email_otp_authenticator.jpa.TrustedIpEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.jboss.logging.Logger;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.UUID;

public class JpaTrustStore implements TrustStore {

    private static final Logger logger = Logger.getLogger(JpaTrustStore.class);

    private final KeycloakSession session;

    public JpaTrustStore(KeycloakSession session) {
        this.session = session;
    }

    private EntityManager getEntityManager() {
        return session.getProvider(JpaConnectionProvider.class).getEntityManager();
    }

    @Override
    public boolean isIpTrusted(RealmModel realm, UserModel user, String ipAddress) {
        TrustedIpEntity entity = findTrustedIp(realm.getId(), user.getId(), ipAddress);
        if (entity == null) {
            return false;
        }
        return !entity.isExpired();
    }

    @Override
    public void trustIp(RealmModel realm, UserModel user, String ipAddress, long expiresAtSeconds) {
        EntityManager em = getEntityManager();
        TrustedIpEntity existing = findTrustedIp(realm.getId(), user.getId(), ipAddress);

        long now = System.currentTimeMillis() / 1000;

        if (existing != null) {
            // Update existing
            existing.setExpiresAt(expiresAtSeconds);
            em.merge(existing);
            logger.debugf("Updated IP trust for user %s, IP %s, expires at %d", user.getId(), ipAddress, expiresAtSeconds);
        } else {
            // Create new
            TrustedIpEntity entity = new TrustedIpEntity(
                UUID.randomUUID().toString(),
                realm.getId(),
                user.getId(),
                ipAddress,
                expiresAtSeconds,
                now
            );
            em.persist(entity);
            logger.debugf("Created IP trust for user %s, IP %s, expires at %d", user.getId(), ipAddress, expiresAtSeconds);
        }
    }

    @Override
    public void refreshIpTrust(RealmModel realm, UserModel user, String ipAddress, long newExpiresAtSeconds) {
        TrustedIpEntity entity = findTrustedIp(realm.getId(), user.getId(), ipAddress);
        if (entity != null) {
            entity.setExpiresAt(newExpiresAtSeconds);
            getEntityManager().merge(entity);
            logger.debugf("Refreshed IP trust for user %s, IP %s, new expiry %d", user.getId(), ipAddress, newExpiresAtSeconds);
        }
    }

    @Override
    public boolean isDeviceTrusted(RealmModel realm, UserModel user, String deviceToken) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            return false;
        }
        TrustedDeviceEntity entity = findTrustedDevice(realm.getId(), user.getId(), deviceToken);
        if (entity == null) {
            return false;
        }
        return !entity.isExpired();
    }

    @Override
    public void trustDevice(RealmModel realm, UserModel user, String deviceToken, long expiresAtSeconds) {
        EntityManager em = getEntityManager();
        long now = System.currentTimeMillis() / 1000;

        // Remove any existing trust for this device token (shouldn't happen, but be safe)
        TrustedDeviceEntity existing = findTrustedDevice(realm.getId(), user.getId(), deviceToken);
        if (existing != null) {
            em.remove(existing);
        }

        TrustedDeviceEntity entity = new TrustedDeviceEntity(
            UUID.randomUUID().toString(),
            realm.getId(),
            user.getId(),
            deviceToken,
            expiresAtSeconds,
            now
        );
        em.persist(entity);
        logger.debugf("Created device trust for user %s, token %s, expires at %d", user.getId(), deviceToken, expiresAtSeconds);
    }

    @Override
    public int cleanupExpired() {
        EntityManager em = getEntityManager();
        long now = System.currentTimeMillis() / 1000;

        int deletedIps = em.createNamedQuery("deleteExpiredTrustedIps")
            .setParameter("currentTime", now)
            .executeUpdate();

        int deletedDevices = em.createNamedQuery("deleteExpiredTrustedDevices")
            .setParameter("currentTime", now)
            .executeUpdate();

        int total = deletedIps + deletedDevices;
        if (total > 0) {
            logger.infof("Cleaned up %d expired trust entries (%d IPs, %d devices)", total, deletedIps, deletedDevices);
        }
        return total;
    }

    @Override
    public void close() {
        // Nothing to close
    }

    private TrustedIpEntity findTrustedIp(String realmId, String userId, String ipAddress) {
        try {
            return getEntityManager()
                .createNamedQuery("findTrustedIp", TrustedIpEntity.class)
                .setParameter("realmId", realmId)
                .setParameter("userId", userId)
                .setParameter("ipAddress", ipAddress)
                .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private TrustedDeviceEntity findTrustedDevice(String realmId, String userId, String deviceToken) {
        try {
            return getEntityManager()
                .createNamedQuery("findTrustedDevice", TrustedDeviceEntity.class)
                .setParameter("realmId", realmId)
                .setParameter("userId", userId)
                .setParameter("deviceToken", deviceToken)
                .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
