package ch.jacem.for_keycloak.email_otp_authenticator.helpers;

import org.keycloak.authentication.AuthenticationFlowContext;

import ch.jacem.for_keycloak.email_otp_authenticator.EmailOTPFormAuthenticatorFactory;

public class ConfigHelper {
    public static int getOtpLifetime(AuthenticationFlowContext context) {
        return ConfigHelper.getConfigIntValue(
            context,
            EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_CODE_LIFETIME,
            EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_CODE_LIFETIME
        );
    }

    public static String getOtpCodeAlphabet(AuthenticationFlowContext context) {
        return ConfigHelper.getConfigStringValue(
            context,
            EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_CODE_ALPHABET,
            EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_CODE_ALPHABET
        );
    }

    public static int getOtpCodeLength(AuthenticationFlowContext context) {
        return ConfigHelper.getConfigIntValue(
            context,
            EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_CODE_LENGTH,
            EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_CODE_LENGTH
        );
    }

    public static String getConfigStringValue(AuthenticationFlowContext context, String key) {
        return getConfigStringValue(context, key, null);
    }

    public static String getConfigStringValue(AuthenticationFlowContext context, String key, String defaultValue) {
        if (null == context.getAuthenticatorConfig() || !context.getAuthenticatorConfig().getConfig().containsKey(key)) {
            return defaultValue;
        }

        String value = context.getAuthenticatorConfig().getConfig().get(key);
        if (null == value || value.isEmpty()) {
            return defaultValue;
        }

        return value;
    }

    public static int getConfigIntValue(AuthenticationFlowContext context, String key) {
        return getConfigIntValue(context, key, 0);
    }

    public static int getConfigIntValue(AuthenticationFlowContext context, String key, int defaultValue) {
        if (null == context.getAuthenticatorConfig() || !context.getAuthenticatorConfig().getConfig().containsKey(key)) {
            return defaultValue;
        }

        String value = context.getAuthenticatorConfig().getConfig().get(key);
        if (null == value || value.isEmpty()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
