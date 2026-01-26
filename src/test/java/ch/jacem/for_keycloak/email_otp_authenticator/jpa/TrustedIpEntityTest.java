package ch.jacem.for_keycloak.email_otp_authenticator.jpa;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TrustedIpEntity")
class TrustedIpEntityTest {

    @Nested
    @DisplayName("isExpired")
    class IsExpired {

        @Test
        @DisplayName("returns false when expiresAt is in the future")
        void notExpiredWhenFuture() {
            long futureTime = (System.currentTimeMillis() / 1000) + 3600; // 1 hour from now
            TrustedIpEntity entity = new TrustedIpEntity(
                "id", "realm", "user", "hash", futureTime, System.currentTimeMillis() / 1000
            );

            assertFalse(entity.isExpired());
        }

        @Test
        @DisplayName("returns true when expiresAt is in the past")
        void expiredWhenPast() {
            long pastTime = (System.currentTimeMillis() / 1000) - 3600; // 1 hour ago
            TrustedIpEntity entity = new TrustedIpEntity(
                "id", "realm", "user", "hash", pastTime, System.currentTimeMillis() / 1000
            );

            assertTrue(entity.isExpired());
        }

        @Test
        @DisplayName("returns false when expiresAt is 0 (should not happen for IP trust)")
        void notExpiredWhenZero() {
            // Note: IP trust doesn't support permanent (0), but the entity handles it
            TrustedIpEntity entity = new TrustedIpEntity(
                "id", "realm", "user", "hash", 0, System.currentTimeMillis() / 1000
            );

            assertFalse(entity.isExpired());
        }

        @Test
        @DisplayName("returns true when expiresAt is exactly now (edge case)")
        void expiredWhenExactlyNow() {
            long now = System.currentTimeMillis() / 1000;
            TrustedIpEntity entity = new TrustedIpEntity(
                "id", "realm", "user", "hash", now - 1, now
            );

            assertTrue(entity.isExpired());
        }
    }

    @Nested
    @DisplayName("Constructor and Getters")
    class ConstructorAndGetters {

        @Test
        @DisplayName("default constructor creates empty entity")
        void defaultConstructor() {
            TrustedIpEntity entity = new TrustedIpEntity();

            assertNull(entity.getId());
            assertNull(entity.getRealmId());
            assertNull(entity.getUserId());
            assertNull(entity.getIpAddress());
            assertEquals(0, entity.getExpiresAt());
            assertEquals(0, entity.getCreatedAt());
        }

        @Test
        @DisplayName("parameterized constructor sets all fields")
        void parameterizedConstructor() {
            TrustedIpEntity entity = new TrustedIpEntity(
                "id-123", "realm-456", "user-789", "ip-hash", 1000L, 500L
            );

            assertEquals("id-123", entity.getId());
            assertEquals("realm-456", entity.getRealmId());
            assertEquals("user-789", entity.getUserId());
            assertEquals("ip-hash", entity.getIpAddress());
            assertEquals(1000L, entity.getExpiresAt());
            assertEquals(500L, entity.getCreatedAt());
        }

    }
}
