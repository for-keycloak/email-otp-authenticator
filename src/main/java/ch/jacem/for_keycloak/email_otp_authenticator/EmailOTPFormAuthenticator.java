package ch.jacem.for_keycloak.email_otp_authenticator;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.security.SecureRandom;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.messages.Messages;
import org.keycloak.sessions.AuthenticationSessionModel;

import ch.jacem.for_keycloak.email_otp_authenticator.authentication.authenticators.conditional.AcceptsFullContextInConfiguredFor;
import ch.jacem.for_keycloak.email_otp_authenticator.helpers.ConfigHelper;

import org.jboss.logging.Logger;

public class EmailOTPFormAuthenticator extends AbstractUsernameFormAuthenticator implements AcceptsFullContextInConfiguredFor
{
    public static final String AUTH_NOTE_OTP_KEY = "for-kc-email-otp-key";
    public static final String AUTH_NOTE_OTP_CREATED_AT = "for-kc-email-otp-created-at";

    public static final String OTP_FORM_TEMPLATE_NAME = "login-email-otp.ftl";
    public static final String OTP_FORM_CODE_INPUT_NAME = "email-otp";
    public static final String OTP_FORM_RESEND_ACTION_NAME = "resend-email";

    public static final String OTP_EMAIL_TEMPLATE_NAME = "otp-email.ftl";
    public static final String OTP_EMAIL_SUBJECT_KEY = "emailOtpSubject";

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
            logger.debug("Resending OTP");

            // Resend a new OTP
            this.sendGeneratedOtp(context);

            // Reshow the form
            context.challenge(
                this.challenge(context, null)
            );

            return;
        }

        String otp = inputData.getFirst(OTP_FORM_CODE_INPUT_NAME);

        if (null == otp) {
            context.challenge(
                this.challenge(context, null)
            );

            return;
        }

        if (otp.isEmpty() || !otp.equals(authenticationSession.getAuthNote(AUTH_NOTE_OTP_KEY))) {
            context.getEvent().user(user).error(Errors.INVALID_USER_CREDENTIALS);
            context.failureChallenge(
                AuthenticationFlowError.INVALID_CREDENTIALS,
                this.challenge(context, "errorInvalidEmailOtp", OTP_FORM_CODE_INPUT_NAME)
            );

            return;
        }

        // Check if the OTP is expired
        if (this.isOtpExpired(context)) {
            // In this case, we generate a new OTP
            this.generateOtp(context);
            this.sendGeneratedOtp(context);

            context.getEvent().user(user).error(Errors.EXPIRED_CODE);
            context.failureChallenge(
                AuthenticationFlowError.INVALID_CREDENTIALS,
                this.challenge(context, "errorExpiredEmailOtp", OTP_FORM_CODE_INPUT_NAME)
            );

            return;
        }

        // OTP is correct
        authenticationSession.removeAuthNote(AUTH_NOTE_OTP_KEY);
        if (!authenticationSession.getAuthenticatedUser().isEmailVerified()) {
            authenticationSession.getAuthenticatedUser().setEmailVerified(true);
        }

        context.success();
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        this.generateOtp(context);
        this.sendGeneratedOtp(context);

        context.challenge(
            this.challenge(context, null)
        );
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
        return null != user.getEmail() && !user.getEmail().isEmpty();
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

    private String generateOtp(AuthenticationFlowContext context) {
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
                this.challenge(context, Messages.INTERNAL_SERVER_ERROR, null)
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
                this.challenge(context, Messages.INVALID_EMAIL, null)
            );

            return;
        }

        try {
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put("otp", otp);
            attributes.put("ttl", ConfigHelper.getOtpLifetime(context));

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
                this.challenge(context, Messages.EMAIL_SENT_ERROR, null)
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
