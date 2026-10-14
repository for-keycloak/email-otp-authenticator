package ch.jacem.for_keycloak.email_otp_authenticator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.stream.Stream;

import jakarta.ws.rs.core.UriInfo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.common.util.Base64Url;
import org.keycloak.crypto.KeyStatus;
import org.keycloak.crypto.KeyUse;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.http.HttpResponse;
import org.keycloak.models.KeyManager;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.jacem.for_keycloak.email_otp_authenticator.trust.TrustStore;

@ExtendWith(MockitoExtension.class)
@DisplayName("Device trust cookie")
class DeviceTrustCookieTest {

    private static final String DEVICE_TOKEN = "550e8400-e29b-41d4-a716-446655440000";
    private static final String REALM_PATH = "/realms/test-realm/";

    private static KeyPair keyPair;

    @Mock
    private AuthenticationFlowContext context;

    @Mock
    private KeycloakSession session;

    @Mock
    private KeycloakContext keycloakContext;

    @Mock
    private RealmModel realm;

    @Mock
    private UserModel user;

    @Mock
    private TrustStore trustStore;

    @Mock
    private HttpResponse httpResponse;

    @Mock
    private UriInfo uriInfo;

    @Mock
    private KeyManager keyManager;

    private EmailOTPFormAuthenticator authenticator;

    @BeforeAll
    static void generateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        authenticator = spy(new EmailOTPFormAuthenticator());
        lenient().doReturn(REALM_PATH).when(authenticator).deviceTrustCookiePath(context);

        lenient().when(context.getSession()).thenReturn(session);
        lenient().when(context.getRealm()).thenReturn(realm);
        lenient().when(context.getUser()).thenReturn(user);
        lenient().doReturn(null).when(authenticator).getCookieValue(eq(context), anyString());
        lenient().when(context.getUriInfo()).thenReturn(uriInfo);
        lenient().when(uriInfo.getBaseUri()).thenReturn(URI.create("https://auth.example.com/"));
        lenient().when(session.getContext()).thenReturn(keycloakContext);
        lenient().when(keycloakContext.getHttpResponse()).thenReturn(httpResponse);
        lenient().when(session.getProvider(TrustStore.class)).thenReturn(trustStore);
        lenient().when(session.keys()).thenReturn(keyManager);

        KeyWrapper key = new KeyWrapper();
        key.setKid("kid-1");
        key.setStatus(KeyStatus.ACTIVE);
        key.setPublicKey(keyPair.getPublic());
        lenient().when(keyManager.getKeysStream(realm, KeyUse.SIG, "RS256")).thenAnswer(i -> Stream.of(key));
    }

    private static String sign(String token) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(token.getBytes(StandardCharsets.UTF_8));
        return token + "." + Base64Url.encode(signature.sign());
    }

    private static RealmModel realmWithId(String id) {
        RealmModel realm = mock(RealmModel.class);
        when(realm.getId()).thenReturn(id);
        return realm;
    }

    private static UserModel userWithId(String id) {
        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    private void givenLegacyCookie(String value) {
        doReturn(value).when(authenticator).getCookieValue(context, EmailOTPFormAuthenticator.DEVICE_TRUST_COOKIE_NAME);
    }

    @Test
    @DisplayName("cookie name is the legacy prefix plus 16 hex characters, and stable for the same realm and user")
    void cookieNameIsStable() {
        String name = EmailOTPFormAuthenticator.deviceTrustCookieName(realmWithId("realm-1"), userWithId("user-1"));

        assertTrue(name.matches("EMAIL_OTP_DEVICE_TRUST_[0-9a-f]{16}"), name);
        assertEquals(name, EmailOTPFormAuthenticator.deviceTrustCookieName(realmWithId("realm-1"), userWithId("user-1")));
    }

    @Test
    @DisplayName("cookie name differs per user and per realm")
    void cookieNameDiffersPerUserAndRealm() {
        String name = EmailOTPFormAuthenticator.deviceTrustCookieName(realmWithId("realm-1"), userWithId("user-1"));

        assertNotEquals(name, EmailOTPFormAuthenticator.deviceTrustCookieName(realmWithId("realm-1"), userWithId("user-2")));
        assertNotEquals(name, EmailOTPFormAuthenticator.deviceTrustCookieName(realmWithId("realm-2"), userWithId("user-1")));
    }

    @Test
    @DisplayName("a legacy cookie of this user is accepted, moved to the per-user cookie for its remaining lifetime, and expired")
    void legacyCookieOfThisUserIsMigrated() throws Exception {
        String signedToken = sign(DEVICE_TOKEN);
        givenLegacyCookie(signedToken);
        long expiresAt = System.currentTimeMillis() / 1000 + 3600;
        when(trustStore.getDeviceTrustExpiresAt(realm, user, DEVICE_TOKEN)).thenReturn(expiresAt);

        assertTrue(authenticator.migrateLegacyDeviceTrustCookie(context, "EMAIL_OTP_DEVICE_TRUST_0123456789abcdef"));

        verify(httpResponse).addHeader(eq("Set-Cookie"), argThat(h ->
            h.startsWith("EMAIL_OTP_DEVICE_TRUST_0123456789abcdef=" + signedToken + ";")
                && h.contains("; Path=" + REALM_PATH + ";")
                && h.matches(".*; Max-Age=(359[0-9]|3600);.*")
                && h.contains("; HttpOnly") && h.contains("; Secure")));
        verify(httpResponse).addHeader(eq("Set-Cookie"), argThat(h ->
            h.startsWith("EMAIL_OTP_DEVICE_TRUST=;") && h.contains("; Path=/;") && h.contains("; Max-Age=0;")));
    }

    @Test
    @DisplayName("a permanent legacy trust stays permanent")
    void permanentLegacyTrustStaysPermanent() throws Exception {
        givenLegacyCookie(sign(DEVICE_TOKEN));
        when(trustStore.getDeviceTrustExpiresAt(realm, user, DEVICE_TOKEN)).thenReturn(0L);

        assertTrue(authenticator.migrateLegacyDeviceTrustCookie(context, "EMAIL_OTP_DEVICE_TRUST_0123456789abcdef"));

        verify(httpResponse).addHeader(eq("Set-Cookie"), argThat(h ->
            h.startsWith("EMAIL_OTP_DEVICE_TRUST_0123456789abcdef=") && h.contains("; Max-Age=" + (10 * 365 * 24 * 60 * 60) + ";")));
    }

    @Test
    @DisplayName("a legacy cookie of another account is left untouched")
    void legacyCookieOfAnotherUserIsLeftAlone() throws Exception {
        givenLegacyCookie(sign(DEVICE_TOKEN));
        when(trustStore.getDeviceTrustExpiresAt(realm, user, DEVICE_TOKEN)).thenReturn(null);

        assertFalse(authenticator.migrateLegacyDeviceTrustCookie(context, "EMAIL_OTP_DEVICE_TRUST_0123456789abcdef"));

        verifyNoInteractions(httpResponse);
    }

    @Test
    @DisplayName("a legacy cookie with a bad signature is ignored without asking the trust store")
    void tamperedLegacyCookieIsIgnored() throws Exception {
        givenLegacyCookie(sign(DEVICE_TOKEN).replaceFirst("\\.", "x."));

        assertFalse(authenticator.migrateLegacyDeviceTrustCookie(context, "EMAIL_OTP_DEVICE_TRUST_0123456789abcdef"));

        verify(trustStore, never()).getDeviceTrustExpiresAt(any(), any(), any());
        verifyNoInteractions(httpResponse);
    }

    @Test
    @DisplayName("nothing happens without a legacy cookie")
    void noLegacyCookie() {
        assertFalse(authenticator.migrateLegacyDeviceTrustCookie(context, "EMAIL_OTP_DEVICE_TRUST_0123456789abcdef"));

        verifyNoInteractions(trustStore, httpResponse);
    }
}
