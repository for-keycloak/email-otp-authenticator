package ch.jacem.for_keycloak.email_otp_authenticator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.provider.ProviderConfigProperty;

@DisplayName("EmailOTPFormAuthenticatorFactory")
class EmailOTPFormAuthenticatorFactoryTest {

    private EmailOTPFormAuthenticatorFactory factory;

    @BeforeEach
    void setUp() {
        factory = new EmailOTPFormAuthenticatorFactory();
    }

    @Nested
    @DisplayName("Factory Metadata")
    class FactoryMetadata {

        @Test
        @DisplayName("getId returns correct provider ID")
        void getIdReturnsProviderId() {
            assertEquals("email-otp-form", factory.getId());
        }

        @Test
        @DisplayName("getDisplayType returns human-readable name")
        void getDisplayType() {
            assertEquals("Email OTP Form", factory.getDisplayType());
        }

        @Test
        @DisplayName("getReferenceCategory returns otp-over-email")
        void getReferenceCategory() {
            assertEquals("otp-over-email", factory.getReferenceCategory());
        }

        @Test
        @DisplayName("getHelpText returns description")
        void getHelpText() {
            assertNotNull(factory.getHelpText());
            assertTrue(factory.getHelpText().contains("OTP"));
        }

        @Test
        @DisplayName("isConfigurable returns true")
        void isConfigurable() {
            assertTrue(factory.isConfigurable());
        }

        @Test
        @DisplayName("isUserSetupAllowed returns false")
        void isUserSetupAllowed() {
            assertFalse(factory.isUserSetupAllowed());
        }

        @Test
        @DisplayName("create returns singleton instance")
        void createReturnsSingleton() {
            Authenticator auth1 = factory.create(null);
            Authenticator auth2 = factory.create(null);

            assertSame(auth1, auth2);
            assertInstanceOf(EmailOTPFormAuthenticator.class, auth1);
        }
    }

    @Nested
    @DisplayName("Requirement Choices")
    class RequirementChoices {

        @Test
        @DisplayName("includes REQUIRED")
        void includesRequired() {
            Requirement[] choices = factory.getRequirementChoices();

            assertTrue(containsRequirement(choices, Requirement.REQUIRED));
        }

        @Test
        @DisplayName("includes ALTERNATIVE")
        void includesAlternative() {
            Requirement[] choices = factory.getRequirementChoices();

            assertTrue(containsRequirement(choices, Requirement.ALTERNATIVE));
        }

        @Test
        @DisplayName("includes DISABLED")
        void includesDisabled() {
            Requirement[] choices = factory.getRequirementChoices();

            assertTrue(containsRequirement(choices, Requirement.DISABLED));
        }

        private boolean containsRequirement(Requirement[] choices, Requirement requirement) {
            for (Requirement r : choices) {
                if (r == requirement) return true;
            }
            return false;
        }
    }

    @Nested
    @DisplayName("Configuration Properties")
    class ConfigurationProperties {

        @Test
        @DisplayName("has expected number of config properties")
        void hasExpectedConfigCount() {
            List<ProviderConfigProperty> props = factory.getConfigProperties();

            assertEquals(10, props.size());
        }

        @Test
        @DisplayName("includes user-role property")
        void includesUserRoleProperty() {
            assertTrue(hasPropertyWithName("user-role"));
        }

        @Test
        @DisplayName("includes negate-user-role property")
        void includesNegateUserRoleProperty() {
            assertTrue(hasPropertyWithName("negate-user-role"));
        }

        @Test
        @DisplayName("includes code-alphabet property")
        void includesCodeAlphabetProperty() {
            assertTrue(hasPropertyWithName("code-alphabet"));
        }

        @Test
        @DisplayName("includes code-length property")
        void includesCodeLengthProperty() {
            assertTrue(hasPropertyWithName("code-length"));
        }

        @Test
        @DisplayName("includes code-lifetime property")
        void includesCodeLifetimeProperty() {
            assertTrue(hasPropertyWithName("code-lifetime"));
        }

        @Test
        @DisplayName("includes ip-trust-enabled property")
        void includesIpTrustEnabledProperty() {
            assertTrue(hasPropertyWithName("ip-trust-enabled"));
        }

        @Test
        @DisplayName("includes ip-trust-duration property")
        void includesIpTrustDurationProperty() {
            assertTrue(hasPropertyWithName("ip-trust-duration"));
        }

        @Test
        @DisplayName("includes device-trust-enabled property")
        void includesDeviceTrustEnabledProperty() {
            assertTrue(hasPropertyWithName("device-trust-enabled"));
        }

        @Test
        @DisplayName("includes device-trust-duration property")
        void includesDeviceTrustDurationProperty() {
            assertTrue(hasPropertyWithName("device-trust-duration"));
        }

        @Test
        @DisplayName("includes trust-only-when-sole property")
        void includesTrustOnlyWhenSoleProperty() {
            assertTrue(hasPropertyWithName("trust-only-when-sole"));
        }

        private boolean hasPropertyWithName(String name) {
            return factory.getConfigProperties().stream()
                .anyMatch(p -> name.equals(p.getName()));
        }
    }

    @Nested
    @DisplayName("Default Values")
    class DefaultValues {

        @Test
        @DisplayName("default code alphabet excludes confusing characters")
        void defaultAlphabetExcludesConfusingChars() {
            String alphabet = EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_CODE_ALPHABET;

            // Should not contain 0, 1, I, O (easily confused)
            assertFalse(alphabet.contains("0"));
            assertFalse(alphabet.contains("1"));
            assertFalse(alphabet.contains("I"));
            assertFalse(alphabet.contains("O"));
        }

        @Test
        @DisplayName("default code length is 6")
        void defaultCodeLength() {
            assertEquals(6, EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_CODE_LENGTH);
        }

        @Test
        @DisplayName("default code lifetime is 600 seconds (10 minutes)")
        void defaultCodeLifetime() {
            assertEquals(600, EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_CODE_LIFETIME);
        }

        @Test
        @DisplayName("IP trust is disabled by default")
        void ipTrustDisabledByDefault() {
            assertFalse(EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_IP_TRUST_ENABLED);
        }

        @Test
        @DisplayName("default IP trust duration is 60 minutes")
        void defaultIpTrustDuration() {
            assertEquals(60, EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_IP_TRUST_DURATION);
        }

        @Test
        @DisplayName("device trust is disabled by default")
        void deviceTrustDisabledByDefault() {
            assertFalse(EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_DEVICE_TRUST_ENABLED);
        }

        @Test
        @DisplayName("default device trust duration is 365 days")
        void defaultDeviceTrustDuration() {
            assertEquals(365, EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_DEVICE_TRUST_DURATION);
        }

        @Test
        @DisplayName("negate user role is false by default")
        void negateRoleFalseByDefault() {
            assertFalse(EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_NEGATE_USER_ROLE);
        }

        @Test
        @DisplayName("user role is null by default (applies to all users)")
        void userRoleNullByDefault() {
            assertNull(EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_USER_ROLE);
        }

        @Test
        @DisplayName("trust only when sole is enabled by default")
        void trustOnlyWhenSoleEnabledByDefault() {
            assertTrue(EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_TRUST_ONLY_WHEN_SOLE);
        }
    }

    @Nested
    @DisplayName("Lifecycle Methods")
    class LifecycleMethods {

        @Test
        @DisplayName("init does not throw")
        void initDoesNotThrow() {
            assertDoesNotThrow(() -> factory.init(null));
        }

        @Test
        @DisplayName("postInit does not throw")
        void postInitDoesNotThrow() {
            assertDoesNotThrow(() -> factory.postInit(null));
        }

        @Test
        @DisplayName("close does not throw")
        void closeDoesNotThrow() {
            assertDoesNotThrow(() -> factory.close());
        }
    }
}
