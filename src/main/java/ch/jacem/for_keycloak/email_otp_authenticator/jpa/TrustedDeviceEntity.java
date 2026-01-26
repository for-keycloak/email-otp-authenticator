package ch.jacem.for_keycloak.email_otp_authenticator.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "EMAIL_OTP_TRUSTED_DEVICE", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"REALM_ID", "USER_ID", "DEVICE_TOKEN"})
})
@NamedQueries({
    @NamedQuery(
        name = "findTrustedDevice",
        query = "SELECT t FROM TrustedDeviceEntity t WHERE t.realmId = :realmId AND t.userId = :userId AND t.deviceToken = :deviceToken"
    ),
    @NamedQuery(
        name = "deleteExpiredTrustedDevices",
        query = "DELETE FROM TrustedDeviceEntity t WHERE t.expiresAt < :currentTime AND t.expiresAt > 0"
    )
})
public class TrustedDeviceEntity {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "REALM_ID", nullable = false, length = 255)
    private String realmId;

    @Column(name = "USER_ID", nullable = false, length = 255)
    private String userId;

    @Column(name = "DEVICE_TOKEN", nullable = false, length = 36)
    private String deviceToken;

    @Column(name = "EXPIRES_AT", nullable = false)
    private long expiresAt;

    @Column(name = "CREATED_AT", nullable = false)
    private long createdAt;

    public TrustedDeviceEntity() {
    }

    public TrustedDeviceEntity(String id, String realmId, String userId, String deviceToken, long expiresAt, long createdAt) {
        this.id = id;
        this.realmId = realmId;
        this.userId = userId;
        this.deviceToken = deviceToken;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRealmId() {
        return realmId;
    }

    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeviceToken() {
        return deviceToken;
    }

    public void setDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isExpired() {
        // expiresAt = 0 means permanent (never expires)
        return expiresAt > 0 && expiresAt < (System.currentTimeMillis() / 1000);
    }
}
