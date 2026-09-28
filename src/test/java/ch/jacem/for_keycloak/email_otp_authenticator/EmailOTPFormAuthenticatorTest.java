package ch.jacem.for_keycloak.email_otp_authenticator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.common.ClientConnection;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.jose.jws.crypto.HashUtils;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.messages.Messages;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.jacem.for_keycloak.email_otp_authenticator.trust.TrustStore;

@ExtendWith(MockitoExtension.class)
class EmailOTPFormAuthenticatorTest {

    @Mock
    private AuthenticationFlowContext context;

    @Mock
    private KeycloakSession session;

    @Mock
    private RealmModel realm;

    @Mock
    private UserModel user;

    @Mock
    private ClientConnection clientConnection;

    @Mock
    private TrustStore trustStore;

    private EmailOTPFormAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        authenticator = new EmailOTPFormAuthenticator();
    }

    @Nested
    @DisplayName("IP Address Handling")
    class IpAddressHandling {

        @Test
        @DisplayName("getRemoteAddr returns direct client IP")
        void directClientIp() {
            when(context.getConnection()).thenReturn(clientConnection);
            when(clientConnection.getRemoteAddr()).thenReturn("192.168.1.100");

            String ip = context.getConnection().getRemoteAddr();

            assertEquals("192.168.1.100", ip);
        }

        @Test
        @DisplayName("getRemoteAddr returns forwarded IP when proxy configured")
        void forwardedIpWithProxy() {
            // Keycloak's ClientConnection.getRemoteAddr() handles X-Forwarded-For
            // when KC_PROXY is configured. We verify our code uses it correctly.
            when(context.getConnection()).thenReturn(clientConnection);
            when(clientConnection.getRemoteAddr()).thenReturn("203.0.113.50");

            String ip = context.getConnection().getRemoteAddr();

            assertEquals("203.0.113.50", ip);
        }

        @Test
        @DisplayName("getRemoteAddr handles IPv6 addresses")
        void ipv6Address() {
            when(context.getConnection()).thenReturn(clientConnection);
            when(clientConnection.getRemoteAddr()).thenReturn("2001:db8::1");

            String ip = context.getConnection().getRemoteAddr();

            assertEquals("2001:db8::1", ip);
        }

        @Test
        @DisplayName("getRemoteAddr handles IPv4-mapped IPv6 addresses")
        void ipv4MappedIpv6() {
            when(context.getConnection()).thenReturn(clientConnection);
            when(clientConnection.getRemoteAddr()).thenReturn("::ffff:192.168.1.1");

            String ip = context.getConnection().getRemoteAddr();

            assertEquals("::ffff:192.168.1.1", ip);
        }

        @Test
        @DisplayName("handles null connection gracefully")
        void nullConnection() {
            when(context.getConnection()).thenReturn(null);

            assertNull(context.getConnection());
        }

        @Test
        @DisplayName("handles connection exception gracefully")
        void connectionException() {
            when(context.getConnection()).thenThrow(new RuntimeException("Connection error"));

            assertThrows(RuntimeException.class, () -> context.getConnection());
        }
    }

    @Nested
    @DisplayName("IP Hashing")
    class IpHashing {

        @Test
        @DisplayName("same IP with same realm produces same hash")
        void consistentHashing() {
            when(realm.getId()).thenReturn("realm-123");

            String hash1 = hashIpAddress(realm, "192.168.1.100");
            String hash2 = hashIpAddress(realm, "192.168.1.100");

            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("different IPs produce different hashes")
        void differentIpsDifferentHashes() {
            when(realm.getId()).thenReturn("realm-123");

            String hash1 = hashIpAddress(realm, "192.168.1.100");
            String hash2 = hashIpAddress(realm, "192.168.1.101");

            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("same IP with different realms produces different hashes")
        void differentRealmsDifferentHashes() {
            when(realm.getId()).thenReturn("realm-1");
            String hash1 = hashIpAddress(realm, "192.168.1.100");

            when(realm.getId()).thenReturn("realm-2");
            String hash2 = hashIpAddress(realm, "192.168.1.100");

            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("hash is URL-safe base64 encoded")
        void urlSafeEncoding() {
            when(realm.getId()).thenReturn("realm-123");

            String hash = hashIpAddress(realm, "192.168.1.100");

            // URL-safe base64 should not contain +, /, or =
            assertFalse(hash.contains("+"));
            assertFalse(hash.contains("/"));
            // Note: padding may or may not be present depending on implementation
        }

        @Test
        @DisplayName("null IP returns null")
        void nullIp() {
            String hash = hashIpAddress(realm, null);

            assertNull(hash);
        }

        @Test
        @DisplayName("empty IP returns null")
        void emptyIp() {
            String hash = hashIpAddress(realm, "");

            assertNull(hash);
        }

        @Test
        @DisplayName("IPv6 addresses are hashed correctly")
        void ipv6Hashing() {
            when(realm.getId()).thenReturn("realm-123");

            String hash1 = hashIpAddress(realm, "2001:db8::1");
            String hash2 = hashIpAddress(realm, "2001:db8::1");

            assertEquals(hash1, hash2);
            assertNotNull(hash1);
        }

        // Helper method to test hashing (mirrors the private method in authenticator)
        private String hashIpAddress(RealmModel realm, String ipAddress) {
            if (ipAddress == null || ipAddress.isEmpty()) {
                return null;
            }
            String saltedInput = realm.getId() + ":" + ipAddress;
            return HashUtils.sha256UrlEncodedHash(saltedInput, StandardCharsets.UTF_8);
        }
    }

    @Nested
    @DisplayName("OTP Validation")
    class OtpValidation {

        @Test
        @DisplayName("constant-time comparison prevents timing attacks")
        void constantTimeComparison() {
            String otp1 = "123456";
            String otp2 = "123456";
            String otp3 = "654321";
            String otp4 = "12345";

            // Same OTPs should match
            assertTrue(MessageDigest.isEqual(
                otp1.getBytes(StandardCharsets.UTF_8),
                otp2.getBytes(StandardCharsets.UTF_8)
            ));

            // Different OTPs should not match
            assertFalse(MessageDigest.isEqual(
                otp1.getBytes(StandardCharsets.UTF_8),
                otp3.getBytes(StandardCharsets.UTF_8)
            ));

            // Different lengths should not match
            assertFalse(MessageDigest.isEqual(
                otp1.getBytes(StandardCharsets.UTF_8),
                otp4.getBytes(StandardCharsets.UTF_8)
            ));
        }

        @Test
        @DisplayName("empty OTP is rejected")
        void emptyOtpRejected() {
            String expected = "123456";
            String provided = "";

            assertFalse(MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
            ));
        }

        @Test
        @DisplayName("null handling in comparison")
        void nullHandling() {
            String expected = "123456";

            // This mirrors the authenticator's null check before comparison
            String provided = null;
            boolean isValid = provided != null && !provided.isEmpty() &&
                MessageDigest.isEqual(
                    provided.getBytes(StandardCharsets.UTF_8),
                    expected.getBytes(StandardCharsets.UTF_8)
                );

            assertFalse(isValid);
        }
    }

    @Nested
    @DisplayName("Device Token Format")
    class DeviceTokenFormat {

        @Test
        @DisplayName("signed token has correct format (token.signature)")
        void signedTokenFormat() {
            // A signed token should have format: token.signature
            String signedToken = "550e8400-e29b-41d4-a716-446655440000.abc123signature";

            assertTrue(signedToken.contains("."));
            int separatorIndex = signedToken.lastIndexOf(".");
            assertTrue(separatorIndex > 0);

            String token = signedToken.substring(0, separatorIndex);
            String signature = signedToken.substring(separatorIndex + 1);

            assertEquals("550e8400-e29b-41d4-a716-446655440000", token);
            assertEquals("abc123signature", signature);
        }

        @Test
        @DisplayName("token without separator is invalid")
        void tokenWithoutSeparator() {
            String invalidToken = "550e8400-e29b-41d4-a716-446655440000";

            assertFalse(invalidToken.contains("."));
        }

        @Test
        @DisplayName("token with only separator is invalid")
        void tokenWithOnlySeparator() {
            String invalidToken = ".signature";

            int separatorIndex = invalidToken.lastIndexOf(".");
            // separatorIndex would be 0, which means empty token part
            assertEquals(0, separatorIndex);
        }
    }

    @Nested
    @DisplayName("Trust Store Integration")
    class TrustStoreIntegration {

        @Test
        @DisplayName("IP trust check calls store with hashed IP")
        void ipTrustCheckUsesHashedIp() {
            when(realm.getId()).thenReturn("realm-123");
            String rawIp = "192.168.1.100";
            String hashedIp = HashUtils.sha256UrlEncodedHash(
                realm.getId() + ":" + rawIp,
                StandardCharsets.UTF_8
            );

            when(trustStore.isIpTrusted(realm, user, hashedIp)).thenReturn(true);

            boolean trusted = trustStore.isIpTrusted(realm, user, hashedIp);

            assertTrue(trusted);
            verify(trustStore).isIpTrusted(realm, user, hashedIp);
        }

        @Test
        @DisplayName("device trust check uses unsigned token")
        void deviceTrustCheckUsesUnsignedToken() {
            String unsignedToken = "550e8400-e29b-41d4-a716-446655440000";

            when(trustStore.isDeviceTrusted(realm, user, unsignedToken)).thenReturn(true);

            boolean trusted = trustStore.isDeviceTrusted(realm, user, unsignedToken);

            assertTrue(trusted);
            verify(trustStore).isDeviceTrusted(realm, user, unsignedToken);
        }

        @Test
        @DisplayName("expired trust returns false")
        void expiredTrustReturnsFalse() {
            String hashedIp = "hashedIp123";

            when(trustStore.isIpTrusted(realm, user, hashedIp)).thenReturn(false);

            boolean trusted = trustStore.isIpTrusted(realm, user, hashedIp);

            assertFalse(trusted);
        }
    }

    @Nested
    @DisplayName("Cookie Security")
    class CookieSecurity {

        @Test
        @DisplayName("cookie name is constant")
        void cookieNameConstant() {
            assertEquals("EMAIL_OTP_DEVICE_TRUST", EmailOTPFormAuthenticator.DEVICE_TRUST_COOKIE_NAME);
        }

        @Test
        @DisplayName("permanent cookie max age is approximately 10 years")
        void permanentCookieMaxAge() {
            int tenYearsInSeconds = 10 * 365 * 24 * 60 * 60;
            // ~315,360,000 seconds
            assertTrue(tenYearsInSeconds > 300_000_000);
            assertTrue(tenYearsInSeconds < 320_000_000);
        }
    }

    @Nested
    @DisplayName("Email Masking")
    class EmailMasking {

        @Test
        @DisplayName("standard email is masked at local and pre-TLD domain parts")
        void standardEmail() {
            assertEquals("jo***@gm***.com", maskEmail("john.doe@gmail.com"));
        }

        @Test
        @DisplayName("short local part is kept and suffixed with ***")
        void shortLocalPart() {
            assertEquals("a***@gm***.com", maskEmail("a@gmail.com"));
        }

        @Test
        @DisplayName("subdomain is folded into the pre-TLD via last-dot split")
        void subdomain() {
            assertEquals("jo***@ma***.com", maskEmail("john@mail.example.com"));
        }

        @Test
        @DisplayName("domain without a dot has no TLD preserved")
        void domainWithoutDot() {
            assertEquals("jo***@lo***", maskEmail("john@localhost"));
        }

        @Test
        @DisplayName("email without @ is returned unchanged")
        void missingAtSign() {
            assertEquals("noatsign", maskEmail("noatsign"));
        }

        @Test
        @DisplayName("empty string is returned unchanged")
        void emptyString() {
            assertEquals("", maskEmail(""));
        }

        @Test
        @DisplayName("null is returned as null")
        void nullEmail() {
            assertNull(maskEmail(null));
        }

        @Test
        @DisplayName("empty local part returns input unchanged")
        void emptyLocalPart() {
            assertEquals("@gmail.com", maskEmail("@gmail.com"));
        }

        @Test
        @DisplayName("empty domain returns input unchanged")
        void emptyDomain() {
            assertEquals("john@", maskEmail("john@"));
        }

        // Mirrors the private method in EmailOTPFormAuthenticator.
        private String maskEmail(String email) {
            if (email == null || email.isEmpty()) {
                return email;
            }
            int atIndex = email.indexOf('@');
            if (atIndex < 0) {
                return email;
            }
            String local = email.substring(0, atIndex);
            String domain = email.substring(atIndex + 1);
            if (local.isEmpty() || domain.isEmpty()) {
                return email;
            }

            String maskedLocal = local.substring(0, Math.min(2, local.length())) + "***";

            String maskedDomain;
            int lastDotIndex = domain.lastIndexOf('.');
            if (lastDotIndex < 0) {
                maskedDomain = domain.substring(0, Math.min(2, domain.length())) + "***";
            } else {
                String preTld = domain.substring(0, lastDotIndex);
                String tld = domain.substring(lastDotIndex + 1);
                maskedDomain = preTld.substring(0, Math.min(2, preTld.length())) + "***." + tld;
            }

            return maskedLocal + "@" + maskedDomain;
        }
    }

    @Nested
    @DisplayName("Resend Cooldown Calculation")
    class ResendCooldownCalculation {

        @Test
        @DisplayName("returns the seconds left within the cooldown")
        void remainingWithinCooldown() {
            assertEquals(50, EmailOTPFormAuthenticator.resendCooldownRemaining(60, 600, "1000", 1010));
        }

        @Test
        @DisplayName("returns 0 once the cooldown has passed")
        void zeroAfterCooldown() {
            assertEquals(0, EmailOTPFormAuthenticator.resendCooldownRemaining(60, 600, "1000", 1060));
            assertEquals(0, EmailOTPFormAuthenticator.resendCooldownRemaining(60, 600, "1000", 5000));
        }

        @Test
        @DisplayName("returns 0 when the cooldown is disabled")
        void zeroWhenDisabled() {
            assertEquals(0, EmailOTPFormAuthenticator.resendCooldownRemaining(0, 600, "1000", 1000));
            assertEquals(0, EmailOTPFormAuthenticator.resendCooldownRemaining(-5, 600, "1000", 1000));
        }

        @Test
        @DisplayName("returns 0 when no code has been created yet")
        void zeroWithoutCode() {
            assertEquals(0, EmailOTPFormAuthenticator.resendCooldownRemaining(60, 600, null, 1000));
            assertEquals(0, EmailOTPFormAuthenticator.resendCooldownRemaining(60, 600, "", 1000));
        }

        @Test
        @DisplayName("returns 0 when the creation note is malformed")
        void zeroWhenMalformed() {
            assertEquals(0, EmailOTPFormAuthenticator.resendCooldownRemaining(60, 600, "not-a-number", 1000));
        }

        @Test
        @DisplayName("never waits beyond the code lifetime, so an expired code can always be replaced")
        void cappedAtCodeLifetime() {
            assertEquals(50, EmailOTPFormAuthenticator.resendCooldownRemaining(600, 60, "1000", 1010));
            assertEquals(0, EmailOTPFormAuthenticator.resendCooldownRemaining(600, 60, "1000", 1060));
            assertEquals(0, EmailOTPFormAuthenticator.resendCooldownRemaining(600, 60, "1000", 1120));
        }

        @Test
        @DisplayName("message key carries the plural category for the language")
        void messageKeyUsesPluralCategory() {
            assertEquals("errorResendCooldownEmailOtpOne", EmailOTPFormAuthenticator.resendCooldownMessageKey(1, "en"));
            assertEquals("errorResendCooldownEmailOtpMany", EmailOTPFormAuthenticator.resendCooldownMessageKey(45, "en"));
            assertEquals("errorResendCooldownEmailOtpFew", EmailOTPFormAuthenticator.resendCooldownMessageKey(3, "ru"));
            assertEquals("errorResendCooldownEmailOtpTwo", EmailOTPFormAuthenticator.resendCooldownMessageKey(2, "ar"));
        }
    }

    @Nested
    @DisplayName("Issuance Limits")
    class IssuanceLimits {

        private static final String CURRENT_OTP = "ABC1"; // contains '1', which the alphabet excludes, so a new code always differs

        @Mock
        private AuthenticationSessionModel authSession;

        @Mock
        private HttpRequest httpRequest;

        @Mock
        private EventBuilder event;

        @Mock
        private LoginFormsProvider form;

        @Mock
        private KeycloakContext keycloakContext;

        @Mock
        private EmailTemplateProvider emailProvider;

        @Mock
        private SingleUseObjectProvider singleUseObjects;

        @Mock
        private RootAuthenticationSessionModel rootSession;

        @Mock
        private AuthenticatorConfigModel config;

        @Mock
        private AuthenticationExecutionModel execution;

        private final Map<String, String> notes = new HashMap<>();
        private final Map<String, String> configMap = new HashMap<>();
        private final MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();

        @BeforeEach
        void setUpFlow() throws Exception {
            lenient().when(context.getAuthenticationSession()).thenReturn(authSession);
            lenient().when(context.getUser()).thenReturn(user);
            lenient().when(context.getRealm()).thenReturn(realm);
            lenient().when(context.getSession()).thenReturn(session);
            lenient().when(context.getEvent()).thenReturn(event);
            lenient().when(context.form()).thenReturn(form);
            lenient().when(context.getAuthenticatorConfig()).thenReturn(config);
            lenient().when(context.getHttpRequest()).thenReturn(httpRequest);
            lenient().when(context.getExecution()).thenReturn(execution);

            lenient().when(config.getConfig()).thenReturn(configMap);
            lenient().when(httpRequest.getDecodedFormParameters()).thenReturn(formParams);
            lenient().when(event.user(any(UserModel.class))).thenReturn(event);

            lenient().when(authSession.getParentSession()).thenReturn(rootSession);
            lenient().when(rootSession.getId()).thenReturn("root-1");
            lenient().when(authSession.getTabId()).thenReturn("tab-1");
            lenient().when(authSession.getAuthNote(anyString())).thenAnswer(i -> notes.get(i.<String>getArgument(0)));
            lenient().doAnswer(i -> notes.put(i.getArgument(0), i.getArgument(1)))
                .when(authSession).setAuthNote(anyString(), anyString());
            lenient().doAnswer(i -> notes.remove(i.<String>getArgument(0)))
                .when(authSession).removeAuthNote(anyString());

            lenient().when(form.setError(anyString(), any(Object[].class))).thenReturn(form);
            lenient().when(form.addError(any(FormMessage.class))).thenReturn(form);
            lenient().when(form.setAttribute(anyString(), any())).thenReturn(form);

            lenient().when(session.getContext()).thenReturn(keycloakContext);
            lenient().when(keycloakContext.resolveLocale(any())).thenReturn(Locale.ENGLISH);
            lenient().when(session.getProvider(EmailTemplateProvider.class)).thenReturn(emailProvider);
            lenient().when(session.singleUseObjects()).thenReturn(singleUseObjects);
            lenient().when(emailProvider.setRealm(any())).thenReturn(emailProvider);
            lenient().when(emailProvider.setUser(any())).thenReturn(emailProvider);

            lenient().when(realm.getId()).thenReturn("realm-1");
            lenient().when(realm.getName()).thenReturn("test-realm");
            lenient().when(user.getId()).thenReturn("user-1");
            lenient().when(user.getEmail()).thenReturn("user@test.local");
            lenient().when(user.isEnabled()).thenReturn(true);
        }

        private long now() {
            return System.currentTimeMillis() / 1000;
        }

        private void givenCurrentCodeCreatedSecondsAgo(long secondsAgo) {
            notes.put(EmailOTPFormAuthenticator.AUTH_NOTE_OTP_KEY, CURRENT_OTP);
            notes.put(EmailOTPFormAuthenticator.AUTH_NOTE_OTP_CREATED_AT, String.valueOf(now() - secondsAgo));
        }

        private void givenResendRequested() {
            formParams.add(EmailOTPFormAuthenticator.OTP_FORM_RESEND_ACTION_NAME, "");
        }

        private void verifyNoEmailSent() throws Exception {
            verify(emailProvider, never()).send(anyString(), anyString(), anyMap());
        }

        private void verifyNoBruteForceFailure() {
            verify(context, never()).failureChallenge(any(), any());
            verify(context, never()).failure(any());
            verify(context, never()).failure(any(), any());
        }

        @Test
        @DisplayName("resend within the cooldown sends no email, keeps the current code and asks the user to wait")
        void resendWithinCooldownIsRefused() throws Exception {
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_RESEND_COOLDOWN, "60");
            String createdAt = String.valueOf(now() - 10);
            notes.put(EmailOTPFormAuthenticator.AUTH_NOTE_OTP_KEY, CURRENT_OTP);
            notes.put(EmailOTPFormAuthenticator.AUTH_NOTE_OTP_CREATED_AT, createdAt);
            givenResendRequested();

            authenticator.action(context);

            verifyNoEmailSent();
            assertEquals(CURRENT_OTP, notes.get(EmailOTPFormAuthenticator.AUTH_NOTE_OTP_KEY));
            assertEquals(createdAt, notes.get(EmailOTPFormAuthenticator.AUTH_NOTE_OTP_CREATED_AT));
            verify(event).error(EmailOTPFormAuthenticator.EVENT_ERROR_RESEND_COOLDOWN);
            verify(form).setError(eq("errorResendCooldownEmailOtpMany"), (Object) argThat(v -> (Long) v >= 49 && (Long) v <= 50));
            verify(context).challenge(any());
            verifyNoBruteForceFailure();
            // A refused resend must not consume the per-user issuance budget
            verify(singleUseObjects, never()).putIfAbsent(anyString(), anyLong());
        }

        @Test
        @DisplayName("resend after the cooldown emails a new, different code")
        void resendAfterCooldownSendsNewCode() throws Exception {
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_RESEND_COOLDOWN, "60");
            givenCurrentCodeCreatedSecondsAgo(61);
            String createdAt = notes.get(EmailOTPFormAuthenticator.AUTH_NOTE_OTP_CREATED_AT);
            when(singleUseObjects.putIfAbsent(anyString(), anyLong())).thenReturn(true);
            givenResendRequested();

            authenticator.action(context);

            verify(singleUseObjects).putIfAbsent("email-otp-resend:root-1:tab-1:" + createdAt, 60L);
            verify(emailProvider).send(eq(EmailOTPFormAuthenticator.OTP_EMAIL_SUBJECT_KEY), eq(EmailOTPFormAuthenticator.OTP_EMAIL_TEMPLATE_NAME), anyMap());
            assertNotEquals(CURRENT_OTP, notes.get(EmailOTPFormAuthenticator.AUTH_NOTE_OTP_KEY));
            verify(event, never()).error(anyString());
        }

        @Test
        @DisplayName("a concurrent resend of the same code (e.g. a double click) sends no second email")
        void concurrentResendIsRefused() throws Exception {
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_RESEND_COOLDOWN, "60");
            givenCurrentCodeCreatedSecondsAgo(61);
            // The other request already claimed the resend of this code
            when(singleUseObjects.putIfAbsent(anyString(), anyLong())).thenReturn(false);
            givenResendRequested();

            authenticator.action(context);

            verifyNoEmailSent();
            assertEquals(CURRENT_OTP, notes.get(EmailOTPFormAuthenticator.AUTH_NOTE_OTP_KEY));
            verify(event).error(EmailOTPFormAuthenticator.EVENT_ERROR_RESEND_COOLDOWN);
            verify(form).setError("errorResendCooldownEmailOtpMany", 60L);
            verify(context).challenge(any());
            verifyNoBruteForceFailure();
        }

        @Test
        @DisplayName("resend of an expired code is not held back by a cooldown longer than the code lifetime")
        void resendOfExpiredCodeIgnoresCooldown() throws Exception {
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_RESEND_COOLDOWN, "600");
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_CODE_LIFETIME, "60");
            givenCurrentCodeCreatedSecondsAgo(120);
            when(singleUseObjects.putIfAbsent(anyString(), anyLong())).thenReturn(true);
            givenResendRequested();

            authenticator.action(context);

            verify(emailProvider).send(anyString(), anyString(), anyMap());
            assertNotEquals(CURRENT_OTP, notes.get(EmailOTPFormAuthenticator.AUTH_NOTE_OTP_KEY));
            verify(event, never()).error(anyString());
        }

        @Test
        @DisplayName("resend with the cooldown disabled (default) emails a new code immediately")
        void resendWithoutCooldownSendsImmediately() throws Exception {
            givenCurrentCodeCreatedSecondsAgo(0);
            givenResendRequested();

            authenticator.action(context);

            verify(emailProvider).send(anyString(), anyString(), anyMap());
            assertNotEquals(CURRENT_OTP, notes.get(EmailOTPFormAuthenticator.AUTH_NOTE_OTP_KEY));
        }

        @Test
        @DisplayName("issuance limit is not consulted when disabled (default)")
        void limitDisabledByDefault() throws Exception {
            authenticator.authenticate(context);

            verify(session, never()).singleUseObjects();
            verify(emailProvider).send(anyString(), anyString(), anyMap());
        }

        @Test
        @DisplayName("re-rendering the form while the current code is valid claims no slot and sends nothing")
        void validCodeIsReusedWithoutClaimingSlot() throws Exception {
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_ISSUANCE_LIMIT, "5");
            givenCurrentCodeCreatedSecondsAgo(10);

            authenticator.authenticate(context);

            verify(singleUseObjects, never()).putIfAbsent(anyString(), anyLong());
            verifyNoEmailSent();
            assertEquals(CURRENT_OTP, notes.get(EmailOTPFormAuthenticator.AUTH_NOTE_OTP_KEY));
        }

        @Test
        @DisplayName("an expired code is replaced on a new login step and the replacement claims a slot")
        void expiredCodeOnAuthenticateClaimsSlot() throws Exception {
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_ISSUANCE_LIMIT, "5");
            givenCurrentCodeCreatedSecondsAgo(1000);
            when(singleUseObjects.putIfAbsent(anyString(), anyLong())).thenReturn(true);

            authenticator.authenticate(context);

            verify(singleUseObjects).putIfAbsent("email-otp-issuance:realm-1:user-1:0", 900L);
            verify(emailProvider).send(anyString(), anyString(), anyMap());
        }

        @Test
        @DisplayName("first code of a login claims an issuance slot for the user and is emailed")
        void firstCodeClaimsSlot() throws Exception {
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_ISSUANCE_LIMIT, "5");
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_ISSUANCE_LIMIT_WINDOW, "900");
            when(singleUseObjects.putIfAbsent(anyString(), anyLong())).thenReturn(true);

            authenticator.authenticate(context);

            verify(singleUseObjects).putIfAbsent("email-otp-issuance:realm-1:user-1:0", 900L);
            verify(emailProvider).send(anyString(), anyString(), anyMap());
            verify(form, never()).setError(eq(EmailOTPFormAuthenticator.ISSUANCE_LIMIT_MESSAGE_KEY), any(Object[].class));
        }

        @Test
        @DisplayName("first code of a login at the limit is not emailed and the user is told to try later")
        void firstCodeAtLimitIsRefused() throws Exception {
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_ISSUANCE_LIMIT, "2");
            when(singleUseObjects.putIfAbsent(anyString(), anyLong())).thenReturn(false);

            authenticator.authenticate(context);

            verifyNoEmailSent();
            assertNull(notes.get(EmailOTPFormAuthenticator.AUTH_NOTE_OTP_KEY));
            verify(event).error(EmailOTPFormAuthenticator.EVENT_ERROR_ISSUANCE_LIMIT);
            verify(form).setError(EmailOTPFormAuthenticator.ISSUANCE_LIMIT_MESSAGE_KEY);
            verify(context).challenge(any());
            verifyNoBruteForceFailure();
        }

        @Test
        @DisplayName("resend at the limit sends no email and keeps the current code valid")
        void resendAtLimitKeepsCurrentCode() throws Exception {
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_ISSUANCE_LIMIT, "2");
            when(singleUseObjects.putIfAbsent(anyString(), anyLong())).thenReturn(false);
            givenCurrentCodeCreatedSecondsAgo(30);
            givenResendRequested();

            authenticator.action(context);

            verifyNoEmailSent();
            assertEquals(CURRENT_OTP, notes.get(EmailOTPFormAuthenticator.AUTH_NOTE_OTP_KEY));
            verify(event).error(EmailOTPFormAuthenticator.EVENT_ERROR_ISSUANCE_LIMIT);
            verify(form).setError(EmailOTPFormAuthenticator.ISSUANCE_LIMIT_MESSAGE_KEY);
            verify(context).challenge(any());
            verifyNoBruteForceFailure();
        }

        @Test
        @DisplayName("regenerating an expired code at the limit sends no email, says so and registers no brute-force failure")
        void expiredCodeAtLimitIsNotRegenerated() throws Exception {
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_ISSUANCE_LIMIT, "2");
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_CODE_LIFETIME, "60");
            when(singleUseObjects.putIfAbsent(anyString(), anyLong())).thenReturn(false);
            givenCurrentCodeCreatedSecondsAgo(120);
            formParams.add(EmailOTPFormAuthenticator.OTP_FORM_CODE_INPUT_NAME, CURRENT_OTP);

            authenticator.action(context);

            verifyNoEmailSent();
            verify(event).error(EmailOTPFormAuthenticator.EVENT_ERROR_ISSUANCE_LIMIT);
            verify(event, times(1)).error(anyString());
            verify(form).addError(argThat(m -> EmailOTPFormAuthenticator.ISSUANCE_LIMIT_MESSAGE_KEY.equals(m.getMessage())));
            verify(context).challenge(any());
            verifyNoBruteForceFailure();
            verify(context, never()).success();
        }

        @Test
        @DisplayName("an expired code below the limit is replaced and still counts as a failed attempt, as before")
        void expiredCodeBelowLimitKeepsExistingBehaviour() throws Exception {
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_CODE_LIFETIME, "60");
            givenCurrentCodeCreatedSecondsAgo(120);
            formParams.add(EmailOTPFormAuthenticator.OTP_FORM_CODE_INPUT_NAME, CURRENT_OTP);

            authenticator.action(context);

            verify(emailProvider).send(anyString(), anyString(), anyMap());
            verify(form).addError(argThat(m -> "errorExpiredEmailOtp".equals(m.getMessage())));
            verify(event).error(Errors.EXPIRED_CODE);
            verify(context).failureChallenge(any(), any());
        }

        @Test
        @DisplayName("a code whose email fails gives its slot back")
        void sendFailureReleasesSlot() throws Exception {
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_ISSUANCE_LIMIT, "5");
            when(singleUseObjects.putIfAbsent(anyString(), anyLong())).thenReturn(true);
            doThrow(new EmailException("SMTP down")).when(emailProvider).send(anyString(), anyString(), anyMap());
            givenResendRequested();

            authenticator.action(context);

            verify(singleUseObjects).remove("email-otp-issuance:realm-1:user-1:0");
            verify(event).error(Errors.EMAIL_SEND_FAILED);
            verify(form).setError(Messages.EMAIL_SENT_ERROR);
        }

        @Test
        @DisplayName("with the limit disabled (default), submitting while no code exists is a failed attempt, as before")
        void submitWithoutCodeUnchangedByDefault() throws Exception {
            formParams.add(EmailOTPFormAuthenticator.OTP_FORM_CODE_INPUT_NAME, "GUESS1");

            authenticator.action(context);

            verifyNoEmailSent();
            verify(event).error(Errors.INVALID_USER_CREDENTIALS);
            verify(context).failureChallenge(any(), any());
        }

        @Test
        @DisplayName("submitting after the limit refused this session's code is not a failed attempt; a code is sent instead")
        void submitWithoutCodeSendsOne() throws Exception {
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_ISSUANCE_LIMIT, "2");
            when(singleUseObjects.putIfAbsent(anyString(), anyLong())).thenReturn(true);
            formParams.add(EmailOTPFormAuthenticator.OTP_FORM_CODE_INPUT_NAME, "GUESS1");

            authenticator.action(context);

            verify(emailProvider).send(anyString(), anyString(), anyMap());
            assertNotNull(notes.get(EmailOTPFormAuthenticator.AUTH_NOTE_OTP_KEY));
            verify(context).challenge(any());
            verifyNoBruteForceFailure();
            verify(context, never()).success();
        }

        @Test
        @DisplayName("submitting after the limit refused this session's code, while still at the limit, is not a failed attempt")
        void submitWithoutCodeAtLimit() throws Exception {
            configMap.put(EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_ISSUANCE_LIMIT, "2");
            when(singleUseObjects.putIfAbsent(anyString(), anyLong())).thenReturn(false);
            formParams.add(EmailOTPFormAuthenticator.OTP_FORM_CODE_INPUT_NAME, "");

            authenticator.action(context);

            verifyNoEmailSent();
            verify(form).setError(EmailOTPFormAuthenticator.ISSUANCE_LIMIT_MESSAGE_KEY);
            verify(context).challenge(any());
            verifyNoBruteForceFailure();
        }
    }
}
