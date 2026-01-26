package ch.jacem.for_keycloak.email_otp_authenticator.jpa;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TrustedDeviceEntity")
class TrustedDeviceEntityTest {

    @Nested
    @DisplayName("isExpired")
    class IsExpired {

        @Test
        @DisplayName("returns false when expiresAt is in the future")
        void notExpiredWhenFuture() {
            long futureTime = (System.currentTimeMillis() / 1000) + 3600;
            TrustedDeviceEntity entity = new TrustedDeviceEntity(
                "id", "realm", "user", "token", futureTime, System.currentTimeMillis() / 1000
            );

            assertFalse(entity.isExpired());
        }

        @Test
        @DisplayName("returns true when expiresAt is in the past")
        void expiredWhenPast() {
            long pastTime = (System.currentTimeMillis() / 1000) - 3600;
            TrustedDeviceEntity entity = new TrustedDeviceEntity(
                "id", "realm", "user", "token", pastTime, System.currentTimeMillis() / 1000
            );

            assertTrue(entity.isExpired());
        }

        @Test
        @DisplayName("returns false when expiresAt is 0 (permanent)")
        void notExpiredWhenPermanent() {
            TrustedDeviceEntity entity = new TrustedDeviceEntity(
                "id", "realm", "user", "token", 0, System.currentTimeMillis() / 1000
            );

            assertFalse(entity.isExpired());
        }

        @Test
        @DisplayName("permanent trust (expiresAt=0) never expires regardless of createdAt")
        void permanentNeverExpires() {
            // Created a long time ago but still permanent
            long veryOldCreatedAt = 0;
            TrustedDeviceEntity entity = new TrustedDeviceEntity(
                "id", "realm", "user", "token", 0, veryOldCreatedAt
            );

            assertFalse(entity.isExpired());
        }

        @Test
        @DisplayName("returns true when expiresAt is 1 second in the past")
        void expiredWhenJustPast() {
            long justPast = (System.currentTimeMillis() / 1000) - 1;
            TrustedDeviceEntity entity = new TrustedDeviceEntity(
                "id", "realm", "user", "token", justPast, System.currentTimeMillis() / 1000
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
            TrustedDeviceEntity entity = new TrustedDeviceEntity();

            assertNull(entity.getId());
            assertNull(entity.getRealmId());
            assertNull(entity.getUserId());
            assertNull(entity.getDeviceToken());
            assertEquals(0, entity.getExpiresAt());
            assertEquals(0, entity.getCreatedAt());
        }

        @Test
        @DisplayName("parameterized constructor sets all fields")
        void parameterizedConstructor() {
            TrustedDeviceEntity entity = new TrustedDeviceEntity(
                "id-123", "realm-456", "user-789", "token-abc", 1000L, 500L
            );

            assertEquals("id-123", entity.getId());
            assertEquals("realm-456", entity.getRealmId());
            assertEquals("user-789", entity.getUserId());
            assertEquals("token-abc", entity.getDeviceToken());
            assertEquals(1000L, entity.getExpiresAt());
            assertEquals(500L, entity.getCreatedAt());
        }

    }
}
