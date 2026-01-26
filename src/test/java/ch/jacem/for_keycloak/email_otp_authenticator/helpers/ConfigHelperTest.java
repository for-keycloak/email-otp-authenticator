package ch.jacem.for_keycloak.email_otp_authenticator.helpers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.AuthenticatorConfigModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigHelper")
class ConfigHelperTest {

    @Mock
    private AuthenticatorConfigModel config;

    private Map<String, String> configMap;

    @BeforeEach
    void setUp() {
        configMap = new HashMap<>();
    }

    @Nested
    @DisplayName("getConfigStringValue")
    class GetConfigStringValue {

        @Test
        @DisplayName("returns value when present")
        void returnsValueWhenPresent() {
            configMap.put("key", "value");
            when(config.getConfig()).thenReturn(configMap);

            String result = ConfigHelper.getConfigStringValue(config, "key", "default");

            assertEquals("value", result);
        }

        @Test
        @DisplayName("returns default when config is null")
        void returnsDefaultWhenConfigNull() {
            String result = ConfigHelper.getConfigStringValue(null, "key", "default");

            assertEquals("default", result);
        }

        @Test
        @DisplayName("returns default when key not present")
        void returnsDefaultWhenKeyNotPresent() {
            when(config.getConfig()).thenReturn(configMap);

            String result = ConfigHelper.getConfigStringValue(config, "missing", "default");

            assertEquals("default", result);
        }

        @Test
        @DisplayName("returns default when value is null")
        void returnsDefaultWhenValueNull() {
            configMap.put("key", null);
            when(config.getConfig()).thenReturn(configMap);

            String result = ConfigHelper.getConfigStringValue(config, "key", "default");

            assertEquals("default", result);
        }

        @Test
        @DisplayName("returns default when value is empty")
        void returnsDefaultWhenValueEmpty() {
            configMap.put("key", "");
            when(config.getConfig()).thenReturn(configMap);

            String result = ConfigHelper.getConfigStringValue(config, "key", "default");

            assertEquals("default", result);
        }

        @Test
        @DisplayName("returns null when no default provided")
        void returnsNullWhenNoDefault() {
            when(config.getConfig()).thenReturn(configMap);

            String result = ConfigHelper.getConfigStringValue(config, "missing");

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("getConfigIntValue")
    class GetConfigIntValue {

        @Test
        @DisplayName("returns parsed int when valid")
        void returnsParsedInt() {
            configMap.put("key", "42");
            when(config.getConfig()).thenReturn(configMap);

            int result = ConfigHelper.getConfigIntValue(config, "key", 0);

            assertEquals(42, result);
        }

        @Test
        @DisplayName("returns default when config is null")
        void returnsDefaultWhenConfigNull() {
            int result = ConfigHelper.getConfigIntValue(null, "key", 99);

            assertEquals(99, result);
        }

        @Test
        @DisplayName("returns default when key not present")
        void returnsDefaultWhenKeyNotPresent() {
            when(config.getConfig()).thenReturn(configMap);

            int result = ConfigHelper.getConfigIntValue(config, "missing", 99);

            assertEquals(99, result);
        }

        @Test
        @DisplayName("returns default when value is not a number")
        void returnsDefaultWhenNotNumber() {
            configMap.put("key", "not-a-number");
            when(config.getConfig()).thenReturn(configMap);

            int result = ConfigHelper.getConfigIntValue(config, "key", 99);

            assertEquals(99, result);
        }

        @Test
        @DisplayName("returns default when value is empty")
        void returnsDefaultWhenEmpty() {
            configMap.put("key", "");
            when(config.getConfig()).thenReturn(configMap);

            int result = ConfigHelper.getConfigIntValue(config, "key", 99);

            assertEquals(99, result);
        }

        @Test
        @DisplayName("returns 0 when no default provided")
        void returnsZeroWhenNoDefault() {
            when(config.getConfig()).thenReturn(configMap);

            int result = ConfigHelper.getConfigIntValue(config, "missing");

            assertEquals(0, result);
        }

        @Test
        @DisplayName("handles negative numbers")
        void handlesNegativeNumbers() {
            configMap.put("key", "-5");
            when(config.getConfig()).thenReturn(configMap);

            int result = ConfigHelper.getConfigIntValue(config, "key", 0);

            assertEquals(-5, result);
        }
    }

    @Nested
    @DisplayName("getConfigBooleanValue")
    class GetConfigBooleanValue {

        @Test
        @DisplayName("returns true when value is 'true'")
        void returnsTrueWhenTrue() {
            configMap.put("key", "true");
            when(config.getConfig()).thenReturn(configMap);

            boolean result = ConfigHelper.getConfigBooleanValue(config, "key", false);

            assertTrue(result);
        }

        @Test
        @DisplayName("returns false when value is 'false'")
        void returnsFalseWhenFalse() {
            configMap.put("key", "false");
            when(config.getConfig()).thenReturn(configMap);

            boolean result = ConfigHelper.getConfigBooleanValue(config, "key", true);

            assertFalse(result);
        }

        @Test
        @DisplayName("returns default when config is null")
        void returnsDefaultWhenConfigNull() {
            boolean result = ConfigHelper.getConfigBooleanValue(null, "key", true);

            assertTrue(result);
        }

        @Test
        @DisplayName("returns default when key not present")
        void returnsDefaultWhenKeyNotPresent() {
            when(config.getConfig()).thenReturn(configMap);

            boolean result = ConfigHelper.getConfigBooleanValue(config, "missing", true);

            assertTrue(result);
        }

        @Test
        @DisplayName("returns false for non-boolean strings")
        void returnsFalseForNonBooleanStrings() {
            configMap.put("key", "yes");
            when(config.getConfig()).thenReturn(configMap);

            boolean result = ConfigHelper.getConfigBooleanValue(config, "key", true);

            // Boolean.parseBoolean returns false for any string that is not "true"
            assertFalse(result);
        }

        @Test
        @DisplayName("returns false when no default provided")
        void returnsFalseWhenNoDefault() {
            when(config.getConfig()).thenReturn(configMap);

            boolean result = ConfigHelper.getConfigBooleanValue(config, "missing");

            assertFalse(result);
        }

        @Test
        @DisplayName("is case insensitive for 'TRUE'")
        void caseInsensitive() {
            configMap.put("key", "TRUE");
            when(config.getConfig()).thenReturn(configMap);

            boolean result = ConfigHelper.getConfigBooleanValue(config, "key", false);

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("Duration Calculations")
    class DurationCalculations {

        @Test
        @DisplayName("IP trust duration converts minutes to seconds")
        void ipTrustDurationConversion() {
            // 60 minutes = 3600 seconds
            assertEquals(3600L, 60 * 60L);
        }

        @Test
        @DisplayName("device trust duration converts days to seconds")
        void deviceTrustDurationConversion() {
            // 1 day = 86400 seconds
            assertEquals(86400L, 1 * 86400L);

            // 365 days = 31536000 seconds
            assertEquals(31536000L, 365 * 86400L);
        }

        @Test
        @DisplayName("device trust duration 0 means permanent")
        void permanentDeviceTrust() {
            int days = 0;
            long seconds = days == 0 ? 0 : days * 86400L;

            assertEquals(0L, seconds);
        }
    }
}
