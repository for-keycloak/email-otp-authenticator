package ch.jacem.for_keycloak.email_otp_authenticator;

import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.common.util.Base64Url;
import org.keycloak.crypto.KeyUse;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.jose.jws.Algorithm;
import org.keycloak.jose.jws.crypto.HashUtils;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.messages.Messages;
import org.keycloak.sessions.AuthenticationSessionModel;

import ch.jacem.for_keycloak.email_otp_authenticator.authentication.authenticators.conditional.AcceptsFullContextInConfiguredFor;
import ch.jacem.for_keycloak.email_otp_authenticator.helpers.ConfigHelper;
import ch.jacem.for_keycloak.email_otp_authenticator.helpers.TrustDurationInfo;
import ch.jacem.for_keycloak.email_otp_authenticator.trust.TrustStore;

import org.jboss.logging.Logger;

public class EmailOTPFormAuthenticator extends AbstractUsernameFormAuthenticator implements AcceptsFullContextInConfiguredFor
{
    public static final String AUTH_NOTE_OTP_KEY = "for-kc-email-otp-key";
    public static final String AUTH_NOTE_OTP_CREATED_AT = "for-kc-email-otp-created-at";

    public static final String OTP_FORM_TEMPLATE_NAME = "login-email-otp.ftl";
    public static final String OTP_FORM_CODE_INPUT_NAME = "email-otp";
    public static final String OTP_FORM_RESEND_ACTION_NAME = "resend-email";
    public static final String OTP_FORM_TRUST_DEVICE_NAME = "trust-device";

    public static final String OTP_EMAIL_TEMPLATE_NAME = "otp-email.ftl";
    public static final String OTP_EMAIL_SUBJECT_KEY = "emailOtpSubject";

    // Cookie name for device trust
    public static final String DEVICE_TRUST_COOKIE_NAME = "EMAIL_OTP_DEVICE_TRUST";

    // ACR values
    public static final String ACR_EMAIL_OTP = "email-otp";
    public static final String ACR_EMAIL_OTP_TRUSTED_DEVICE = "email-otp-trusted-device";
    public static final String ACR_EMAIL_OTP_TRUSTED_IP = "email-otp-trusted-ip";

    private static final Logger logger = Logger.getLogger(EmailOTPFormAuthenticator.class);

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> inputData = context.getHttpRequest().getDecodedFormParameters();
        AuthenticationSessionModel authenticationSession = context.getAuthenticationSession();

        UserModel user = context.getUser();
        boolean userEnabled = this.enabledUser(context, user);
        // the brute force lock might be lifted/user enabled in the meantime -> we need to clear the auth session note
        if (userEnabled) {
            context.getAuthenticationSession().removeAuthNote(AbstractUsernameFormAuthenticator.SESSION_INVALID);
        }
        if("true".equals(context.getAuthenticationSession().getAuthNote(AbstractUsernameFormAuthenticator.SESSION_INVALID))) {
            context.getEvent().user(context.getUser()).error(Errors.INVALID_AUTHENTICATION_SESSION);
            // challenge already set by calling enabledUser() above
            return;
        }
        if (!userEnabled) {
            // error in context is set in enabledUser/isDisabledByBruteForce
            context.getAuthenticationSession().setAuthNote(AbstractUsernameFormAuthenticator.SESSION_INVALID, "true");
            return;
        }

        if (inputData.containsKey(OTP_FORM_RESEND_ACTION_NAME)) {
            logger.debug("Resending a new OTP");

            // Regenerate and resend a new OTP
            this.generateOtp(context, true);

            // Reshow the form
            context.challenge(
                this.buildOtpForm(context, null, null)
            );

            return;
        }

        String otp = inputData.getFirst(OTP_FORM_CODE_INPUT_NAME);

        if (null == otp) {
            context.challenge(
                this.buildOtpForm(context, null, null)
            );

            return;
        }

        String expectedOtp = authenticationSession.getAuthNote(AUTH_NOTE_OTP_KEY);
        if (otp.isEmpty() || expectedOtp == null || !MessageDigest.isEqual(
                otp.getBytes(StandardCharsets.UTF_8),
                expectedOtp.getBytes(StandardCharsets.UTF_8))) {
            context.getEvent().user(user).error(Errors.INVALID_USER_CREDENTIALS);
            context.failureChallenge(
                AuthenticationFlowError.INVALID_CREDENTIALS,
                this.buildOtpForm(context, "errorInvalidEmailOtp", OTP_FORM_CODE_INPUT_NAME)
            );

            return;
        }

        // Check if the OTP is expired
        if (this.isOtpExpired(context)) {
            // In this case, we generate a new OTP
            this.generateOtp(context, true);

            context.getEvent().user(user).error(Errors.EXPIRED_CODE);
            context.failureChallenge(
                AuthenticationFlowError.INVALID_CREDENTIALS,
                this.buildOtpForm(context, "errorExpiredEmailOtp", OTP_FORM_CODE_INPUT_NAME)
            );

            return;
        }

        // OTP is correct
        authenticationSession.removeAuthNote(AUTH_NOTE_OTP_KEY);
        if (!authenticationSession.getAuthenticatedUser().isEmailVerified()) {
            authenticationSession.getAuthenticatedUser().setEmailVerified(true);
        }

        // Store trust entries
        storeTrustEntries(context, inputData);

        // Set ACR for actual OTP entry
        setAcr(context, ACR_EMAIL_OTP);

        context.success();
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // Check role condition - if user doesn't match criteria, skip this authenticator
        if (!this.shouldRequireOtp(context)) {
            // In REQUIRED mode: use success() to skip (allows role-based filtering)
            // In ALTERNATIVE mode: use attempted() to prevent 2FA bypass
            if (context.getExecution().isRequired()) {
                context.success();
            } else {
                context.attempted();
            }
            return;
        }

        UserModel user = context.getUser();
        RealmModel realm = context.getRealm();

        // Check if trust should be applied based on flow configuration
        boolean shouldApplyTrust = shouldApplyTrust(context);

        // Check device trust first (highest priority)
        if (shouldApplyTrust && ConfigHelper.isDeviceTrustEnabled(context)) {
            String signedToken = getDeviceTokenFromCookie(context);
            if (signedToken != null) {
                // Verify the signature and extract the original token
                String deviceToken = verifyDeviceToken(context.getSession(), realm, signedToken);
                if (deviceToken != null) {
                    TrustStore trustStore = getTrustStore(context);
                    if (trustStore != null && trustStore.isDeviceTrusted(realm, user, deviceToken)) {
                        logger.debugf("Device is trusted for user %s, skipping OTP", user.getId());
                        setAcr(context, ACR_EMAIL_OTP_TRUSTED_DEVICE);
                        context.success();
                        return;
                    }
                } else {
                    logger.debug("Device token signature verification failed");
                }
            }
        }

        // Check IP trust (second priority)
        if (shouldApplyTrust && ConfigHelper.isIpTrustEnabled(context)) {
            String clientIp = getClientIpAddress(context);
            if (clientIp != null) {
                // Hash the IP for privacy-preserving lookup
                String hashedIp = hashIpAddress(realm, clientIp);
                TrustStore trustStore = getTrustStore(context);
                if (trustStore != null && trustStore.isIpTrusted(realm, user, hashedIp)) {
                    logger.debugf("IP is trusted for user %s, skipping OTP", user.getId());
                    // Refresh the rolling expiration
                    long newExpiresAt = (System.currentTimeMillis() / 1000) + ConfigHelper.getIpTrustDurationSeconds(context);
                    trustStore.refreshIpTrust(realm, user, hashedIp, newExpiresAt);
                    setAcr(context, ACR_EMAIL_OTP_TRUSTED_IP);
                    context.success();
                    return;
                }
            }
        }

        // No trust found, require OTP
        this.generateOtp(context, false);

        context.challenge(
            this.buildOtpForm(context, null, null)
        );
    }

    private void storeTrustEntries(AuthenticationFlowContext context, MultivaluedMap<String, String> inputData) {
        UserModel user = context.getUser();
        RealmModel realm = context.getRealm();
        TrustStore trustStore = getTrustStore(context);

        if (trustStore == null) {
            logger.warn("TrustStore provider not available, cannot store trust entries");
            return;
        }

        long now = System.currentTimeMillis() / 1000;

        // Store IP trust if enabled
        if (ConfigHelper.isIpTrustEnabled(context)) {
            String clientIp = getClientIpAddress(context);
            if (clientIp != null) {
                // Hash IP for privacy-preserving storage
                String hashedIp = hashIpAddress(realm, clientIp);
                long expiresAt = now + ConfigHelper.getIpTrustDurationSeconds(context);
                trustStore.trustIp(realm, user, hashedIp, expiresAt);
                logger.debugf("Stored IP trust for user %s", user.getId());
            }
        }

        // Store device trust if enabled AND checkbox was checked
        if (ConfigHelper.isDeviceTrustEnabled(context)) {
            String trustDevice = inputData.getFirst(OTP_FORM_TRUST_DEVICE_NAME);
            if ("true".equals(trustDevice)) {
                String deviceToken = UUID.randomUUID().toString();
                long durationSeconds = ConfigHelper.getDeviceTrustDurationSeconds(context);
                long expiresAt = (durationSeconds == 0) ? 0 : now + durationSeconds;

                // Store the unsigned token in database
                trustStore.trustDevice(realm, user, deviceToken, expiresAt);

                // Sign the token before putting in cookie
                String signedToken = signDeviceToken(context.getSession(), realm, deviceToken);
                if (signedToken != null) {
                    setDeviceTrustCookie(context, signedToken, durationSeconds);
                } else {
                    logger.warn("Could not sign device token, device trust cookie not set");
                }
                logger.debugf("Stored device trust for user %s", user.getId());
            }
        }
    }

    private TrustStore getTrustStore(AuthenticationFlowContext context) {
        try {
            return context.getSession().getProvider(TrustStore.class);
        } catch (Exception e) {
            logger.warn("Failed to get TrustStore provider", e);
            return null;
        }
    }

    private String getClientIpAddress(AuthenticationFlowContext context) {
        // Use Keycloak's ClientConnection which respects proxy configuration
        // (KC_PROXY=edge, KC_PROXY=passthrough, etc.)
        try {
            return context.getConnection().getRemoteAddr();
        } catch (Exception e) {
            logger.warn("Could not determine client IP address", e);
            return null;
        }
    }

    /**
     * Hashes an IP address using SHA-256 with realm ID as salt.
     * Uses Keycloak's HashUtils for the hash computation.
     */
    private String hashIpAddress(RealmModel realm, String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return null;
        }
        // Use realm ID as salt for domain separation
        String saltedInput = realm.getId() + ":" + ipAddress;
        return HashUtils.sha256UrlEncodedHash(saltedInput, StandardCharsets.UTF_8);
    }

    /**
     * Signs a device token using RSA-SHA256 with Keycloak's managed key.
     * Uses Keycloak's key management for signing. RS256 keys are always
     * available in Keycloak realms (used for JWT signing).
     * Returns format: token.signature
     */
    private String signDeviceToken(KeycloakSession session, RealmModel realm, String token) {
        try {
            // Get the active RS256 key from Keycloak's key management (always available)
            KeyWrapper key = session.keys().getActiveKey(realm, KeyUse.SIG, Algorithm.RS256.name());

            if (key == null || key.getPrivateKey() == null) {
                logger.error("No RS256 signing key available in realm - this should not happen");
                return null;
            }

            // Sign using RSA-SHA256
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign((PrivateKey) key.getPrivateKey());
            signature.update(token.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signature.sign();

            return token + "." + Base64Url.encode(signatureBytes);
        } catch (Exception e) {
            logger.warn("Failed to sign device token", e);
            return null;
        }
    }

    /**
     * Verifies a signed device token using RSA-SHA256.
     * Tries all available RS256 keys (active and passive) to handle key rotation -
     * tokens signed with rotated keys can still be verified.
     * Returns the original token if valid, null if invalid.
     */
    private String verifyDeviceToken(KeycloakSession session, RealmModel realm, String signedToken) {
        if (signedToken == null || !signedToken.contains(".")) {
            return null;
        }

        int separatorIndex = signedToken.lastIndexOf(".");
        if (separatorIndex <= 0) {
            return null;
        }

        String token = signedToken.substring(0, separatorIndex);
        String signatureBase64 = signedToken.substring(separatorIndex + 1);

        try {
            byte[] signatureBytes = Base64Url.decode(signatureBase64);

            // Get all RS256 keys (active and passive) for verification
            // This handles key rotation - old tokens signed with rotated keys can still be verified
            List<KeyWrapper> keys = session.keys().getKeysStream(realm, KeyUse.SIG, Algorithm.RS256.name())
                .filter(k -> k.getStatus().isEnabled() && k.getPublicKey() != null)
                .toList();

            if (keys.isEmpty()) {
                logger.error("No RS256 keys available for verification");
                return null;
            }

            // Try each key (handles key rotation)
            for (KeyWrapper key : keys) {
                try {
                    Signature signature = Signature.getInstance("SHA256withRSA");
                    signature.initVerify((PublicKey) key.getPublicKey());
                    signature.update(token.getBytes(StandardCharsets.UTF_8));

                    if (signature.verify(signatureBytes)) {
                        return token;
                    }
                } catch (Exception e) {
                    // Try next key
                    logger.debugf("Verification with key %s failed, trying next", key.getKid());
                }
            }

            logger.debug("Device token signature verification failed - no matching key");
        } catch (Exception e) {
            logger.debug("Token verification failed", e);
        }

        return null;
    }

    private String getDeviceTokenFromCookie(AuthenticationFlowContext context) {
        Map<String, Cookie> cookies = context.getHttpRequest().getHttpHeaders().getCookies();
        if (cookies != null) {
            Cookie cookie = cookies.get(DEVICE_TRUST_COOKIE_NAME);
            if (cookie != null) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void setDeviceTrustCookie(AuthenticationFlowContext context, String deviceToken, long durationSeconds) {
        // Calculate max age
        int maxAge;
        if (durationSeconds == 0) {
            // Permanent: set to ~10 years
            maxAge = 10 * 365 * 24 * 60 * 60;
        } else {
            maxAge = (int) durationSeconds;
        }

        // Determine if we should use secure cookies (HTTPS)
        boolean secure = "https".equalsIgnoreCase(context.getUriInfo().getBaseUri().getScheme());

        // Build the Set-Cookie header value manually for maximum compatibility
        StringBuilder cookieBuilder = new StringBuilder();
        cookieBuilder.append(DEVICE_TRUST_COOKIE_NAME).append("=").append(deviceToken);
        cookieBuilder.append("; Path=/");
        cookieBuilder.append("; Max-Age=").append(maxAge);
        cookieBuilder.append("; HttpOnly");
        cookieBuilder.append("; SameSite=Lax");
        if (secure) {
            cookieBuilder.append("; Secure");
        }

        // Add the cookie header to the response
        context.getSession().getContext().getHttpResponse().addHeader("Set-Cookie", cookieBuilder.toString());
        logger.debugf("Set device trust cookie for token %s with max-age %d", deviceToken, maxAge);
    }

    private void setAcr(AuthenticationFlowContext context, String acr) {
        context.getAuthenticationSession().setAuthNote("acr", acr);
    }

    private boolean shouldRequireOtp(AuthenticationFlowContext context) {
        RealmModel realm = context.getRealm();
        UserModel user = context.getUser();

        if (null == user) {
            return false;
        }

        String configuredRole = ConfigHelper.getRole(context);
        if (null != configuredRole && !configuredRole.isEmpty()) {
            RoleModel role = realm.getRole(configuredRole);
            if (null != role && user.hasRole(role) == ConfigHelper.getNegateRole(context)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Determines if trust (IP/device) should be applied based on flow configuration.
     * When "Trust Only When Sole Authenticator" is enabled (default), trust is skipped
     * if this authenticator is configured as ALTERNATIVE (meaning there are other options).
     */
    private boolean shouldApplyTrust(AuthenticationFlowContext context) {
        // If the setting is disabled, always apply trust
        if (!ConfigHelper.isTrustOnlyWhenSole(context)) {
            return true;
        }

        // If we're configured as ALTERNATIVE, don't apply trust
        // because the user explicitly chose this method over alternatives
        if (context.getExecution().isAlternative()) {
            logger.debug("Trust skipped: authenticator is alternative and trust-only-when-sole is enabled");
            return false;
        }

        return true;
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    protected String disabledByBruteForceFieldError() {
        return OTP_FORM_CODE_INPUT_NAME;
    }

    @Override
    protected Response createLoginForm(LoginFormsProvider form) {
        return form.createForm(OTP_FORM_TEMPLATE_NAME);
    }

    private Response buildOtpForm(AuthenticationFlowContext context, String errorMessage, String field) {
        LoginFormsProvider form = context.form();

        if (errorMessage != null) {
            if (field != null) {
                form.addError(new org.keycloak.models.utils.FormMessage(field, errorMessage));
            } else {
                form.setError(errorMessage);
            }
        }

        // Add device trust info to form if enabled
        boolean deviceTrustEnabled = ConfigHelper.isDeviceTrustEnabled(context);
        form.setAttribute("deviceTrustEnabled", deviceTrustEnabled);

        if (deviceTrustEnabled) {
            int trustDays = ConfigHelper.getDeviceTrustDurationDays(context);
            boolean permanent = (trustDays == 0);
            form.setAttribute("deviceTrustPermanent", permanent);

            if (!permanent) {
                TrustDurationInfo durationInfo = TrustDurationInfo.fromDays(trustDays);
                if (durationInfo != null) {
                    String lang = context.getSession().getContext().resolveLocale(context.getUser()).getLanguage();
                    form.setAttribute("trustDurationValue", durationInfo.getValue());
                    form.setAttribute("trustDurationUnitKey", durationInfo.getUnitMessageKey(lang));
                    // In Arabic, hide the number when value is 1 (uses singular form without numeral)
                    form.setAttribute("trustHideNumber", "ar".equals(lang) && durationInfo.getValue() == 1);
                }
            }
        }

        return form.createForm(OTP_FORM_TEMPLATE_NAME);
    }

    @Override
    public boolean configuredFor(AuthenticationFlowContext context, AuthenticatorConfigModel config) {
        RealmModel realm = context.getRealm();
        UserModel user = context.getUser();

        if (null == user) {
            return false;
        }

        String configuredRole = ConfigHelper.getRole(config);
        if (null != configuredRole && !configuredRole.isEmpty()) {
            RoleModel role = realm.getRole(configuredRole);
            if (null != role && user.hasRole(role) == ConfigHelper.getNegateRole(config)) {
                return false;
            }
        }

        return null != user.getEmail() && !user.getEmail().isEmpty();
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return user != null && user.getEmail() != null && !user.getEmail().isEmpty();
    }

    @Override
    public boolean areRequiredActionsEnabled(KeycloakSession session, RealmModel realm) {
        return false;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public List<RequiredActionFactory> getRequiredActions(KeycloakSession session) {
        return null;
    }

    @Override
    public void close() {
    }

    private String generateOtp(AuthenticationFlowContext context, boolean forceRegenerate) {
        // If the OTP is already set in the auth session and we are not forcing a regeneration, return it
        String existingOtp = context.getAuthenticationSession().getAuthNote(AUTH_NOTE_OTP_KEY);
        if (!forceRegenerate && existingOtp != null && !existingOtp.isEmpty() && !this.isOtpExpired(context)) {
            return existingOtp;
        }

        String alphabet = ConfigHelper.getOtpCodeAlphabet(context);
        int length = ConfigHelper.getOtpCodeLength(context);

        // Generate a random `length` character string based on the `alphabet`
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder otpBuilder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            otpBuilder.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
        }
        String otp = otpBuilder.toString();

        context.getAuthenticationSession().setAuthNote(AUTH_NOTE_OTP_CREATED_AT, String.valueOf(System.currentTimeMillis() / 1000));
        context.getAuthenticationSession().setAuthNote(AUTH_NOTE_OTP_KEY, otp);

        this.sendGeneratedOtp(context);

        return otp;
    }

    private void sendGeneratedOtp(AuthenticationFlowContext context) {
        // If the OTP is not set in the auth session, fail
        String otp = context.getAuthenticationSession().getAuthNote(AUTH_NOTE_OTP_KEY);
        if (null == otp || otp.isEmpty()) {
            logger.error("OTP is not set in the auth session.");

            context.getEvent().user(context.getUser()).error(Errors.INVALID_USER_CREDENTIALS);
            context.failureChallenge(
                AuthenticationFlowError.INTERNAL_ERROR,
                this.buildOtpForm(context, Messages.INTERNAL_SERVER_ERROR, null)
            );

            return;
        }

        UserModel user = context.getUser();
        String email = user.getEmail();

        if (email == null || email.isEmpty()) {
            logger.error("User does not have an email address configured.");

            context.getEvent().user(user).error(Errors.INVALID_EMAIL);
            context.failureChallenge(
                AuthenticationFlowError.INVALID_USER,
                this.buildOtpForm(context, Messages.INVALID_EMAIL, null)
            );

            return;
        }

        try {
            Map<String, Object> attributes = new HashMap<String, Object>();
            int ttlSeconds = ConfigHelper.getOtpLifetime(context);
            attributes.put("otp", otp);
            attributes.put("ttl", ttlSeconds);
            attributes.put("ttlMinutes", ttlSeconds / 60);

            context.getSession()
                .getProvider(EmailTemplateProvider.class)
                .setRealm(context.getRealm())
                .setUser(user)
                .send(
                    OTP_EMAIL_SUBJECT_KEY,
                    OTP_EMAIL_TEMPLATE_NAME,
                    attributes
                );

            logger.debug("OTP email sent to " + user.getUsername());
        } catch (Exception e) {
            logger.error("Failed to send OTP email", e);

            context.getEvent().user(user).error(Errors.EMAIL_SEND_FAILED);
            context.failureChallenge(
                AuthenticationFlowError.INTERNAL_ERROR,
                this.buildOtpForm(context, Messages.EMAIL_SENT_ERROR, null)
            );
        }
    }

    private boolean isOtpExpired(AuthenticationFlowContext context) {
        int lifetime = ConfigHelper.getOtpLifetime(context);
        long createdAt = Long.parseLong(context.getAuthenticationSession().getAuthNote(AUTH_NOTE_OTP_CREATED_AT));
        long now = System.currentTimeMillis() / 1000;

        return ((now - lifetime) > createdAt);
    }
}
