package ch.jacem.for_keycloak.email_otp_authenticator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.services.managers.DefaultBruteForceProtector;

// Scoped to the keycloak-26.6.2 profile via build-helper add-test-source (see pom.xml).
// DefaultBruteForceProtector.ALLOWED_AUTHENTICATION_CATEGORIES was introduced in Keycloak 26.6,
// so this source tree is only compiled/run on 26.6+ and is excluded from the older matrix builds,
// where the category allow-list (and this whole mechanism) does not exist.
@DisplayName("EmailOTPFormAuthenticatorFactory brute-force category (Keycloak 26.6+)")
class EmailOTPFormAuthenticatorFactoryBruteForceCategoryTest {

    @Test
    @DisplayName("getReferenceCategory is in DefaultBruteForceProtector's allow-list")
    void getReferenceCategoryIsAllowedForBruteForce() {
        EmailOTPFormAuthenticatorFactory factory = new EmailOTPFormAuthenticatorFactory();

        assertTrue(
            DefaultBruteForceProtector.ALLOWED_AUTHENTICATION_CATEGORIES
                .contains(factory.getReferenceCategory())
        );
    }
}
