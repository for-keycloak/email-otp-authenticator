package ch.jacem.for_keycloak.email_otp_authenticator.trust;

import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.Provider;

/**
 * Provider for managing trusted IPs and devices for email OTP bypass.
 */
public interface TrustStore extends Provider {

    /**
     * Check if an IP address is trusted for a user.
     *
     * @param realm The realm
     * @param user The user
     * @param ipAddress The IP address to check
     * @return true if the IP is trusted and not expired
     */
    boolean isIpTrusted(RealmModel realm, UserModel user, String ipAddress);

    /**
     * Add or update a trusted IP for a user with rolling expiration.
     *
     * @param realm The realm
     * @param user The user
     * @param ipAddress The IP address to trust
     * @param expiresAtSeconds Unix timestamp when the trust expires
     */
    void trustIp(RealmModel realm, UserModel user, String ipAddress, long expiresAtSeconds);

    /**
     * Refresh the expiration of an existing trusted IP.
     *
     * @param realm The realm
     * @param user The user
     * @param ipAddress The IP address
     * @param newExpiresAtSeconds New expiration timestamp
     */
    void refreshIpTrust(RealmModel realm, UserModel user, String ipAddress, long newExpiresAtSeconds);

    /**
     * Check if a device token is trusted for a user.
     *
     * @param realm The realm
     * @param user The user
     * @param deviceToken The device token from cookie
     * @return true if the device is trusted and not expired
     */
    boolean isDeviceTrusted(RealmModel realm, UserModel user, String deviceToken);

    /**
     * Add a trusted device for a user.
     *
     * @param realm The realm
     * @param user The user
     * @param deviceToken The unique device token
     * @param expiresAtSeconds Unix timestamp when trust expires (0 = permanent)
     */
    void trustDevice(RealmModel realm, UserModel user, String deviceToken, long expiresAtSeconds);

    /**
     * Clean up expired trust entries.
     *
     * @return Number of entries removed
     */
    int cleanupExpired();
}
