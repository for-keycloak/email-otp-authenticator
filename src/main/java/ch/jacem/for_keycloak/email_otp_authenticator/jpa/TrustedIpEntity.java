package ch.jacem.for_keycloak.email_otp_authenticator.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "EMAIL_OTP_TRUSTED_IP", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"REALM_ID", "USER_ID", "IP_ADDRESS"})
})
@NamedQueries({
    @NamedQuery(
        name = "findTrustedIp",
        query = "SELECT t FROM TrustedIpEntity t WHERE t.realmId = :realmId AND t.userId = :userId AND t.ipAddress = :ipAddress"
    ),
    @NamedQuery(
        name = "deleteExpiredTrustedIps",
        query = "DELETE FROM TrustedIpEntity t WHERE t.expiresAt < :currentTime AND t.expiresAt > 0"
    )
})
public class TrustedIpEntity {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "REALM_ID", nullable = false, length = 255)
    private String realmId;

    @Column(name = "USER_ID", nullable = false, length = 255)
    private String userId;

    @Column(name = "IP_ADDRESS", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "EXPIRES_AT", nullable = false)
    private long expiresAt;

    @Column(name = "CREATED_AT", nullable = false)
    private long createdAt;

    public TrustedIpEntity() {
    }

    public TrustedIpEntity(String id, String realmId, String userId, String ipAddress, long expiresAt, long createdAt) {
        this.id = id;
        this.realmId = realmId;
        this.userId = userId;
        this.ipAddress = ipAddress;
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

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
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
        return expiresAt > 0 && expiresAt < (System.currentTimeMillis() / 1000);
    }
}
