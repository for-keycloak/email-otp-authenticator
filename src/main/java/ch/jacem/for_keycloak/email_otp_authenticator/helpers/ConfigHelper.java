package ch.jacem.for_keycloak.email_otp_authenticator.helpers;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.models.AuthenticatorConfigModel;

import ch.jacem.for_keycloak.email_otp_authenticator.EmailOTPFormAuthenticatorFactory;

public class ConfigHelper {

    private static final Logger logger = Logger.getLogger(ConfigHelper.class);

    // Settings are read on every login; remember which bad values were reported so each is logged once
    private static final Set<String> reportedValues = ConcurrentHashMap.newKeySet();

    public static String getRole(AuthenticatorConfigModel config) {
        return ConfigHelper.getConfigStringValue(
            config,
            EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_USER_ROLE,
            EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_USER_ROLE
        );
    }

    public static String getRole(AuthenticationFlowContext context) {
        return ConfigHelper.getRole(context.getAuthenticatorConfig());
    }

    public static boolean getNegateRole(AuthenticatorConfigModel config) {
        return ConfigHelper.getConfigBooleanValue(
            config,
            EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_NEGATE_USER_ROLE,
            EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_NEGATE_USER_ROLE
        );
    }

    public static boolean getNegateRole(AuthenticationFlowContext context) {
        return ConfigHelper.getNegateRole(context.getAuthenticatorConfig());
    }

    public static int getOtpLifetime(AuthenticatorConfigModel config) {
        return ConfigHelper.getConfigIntValue(
            config,
            EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_CODE_LIFETIME,
            EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_CODE_LIFETIME
        );
    }

    public static int getOtpLifetime(AuthenticationFlowContext context) {
        return ConfigHelper.getOtpLifetime(context.getAuthenticatorConfig());
    }

    public static String getOtpCodeAlphabet(AuthenticatorConfigModel config) {
        return ConfigHelper.getConfigStringValue(
            config,
            EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_CODE_ALPHABET,
            EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_CODE_ALPHABET
        );
    }

    public static String getOtpCodeAlphabet(AuthenticationFlowContext context) {
        return ConfigHelper.getOtpCodeAlphabet(context.getAuthenticatorConfig());
    }

    public static int getOtpCodeLength(AuthenticatorConfigModel config) {
        return ConfigHelper.getConfigIntValue(
            config,
            EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_CODE_LENGTH,
            EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_CODE_LENGTH
        );
    }

    public static int getOtpCodeLength(AuthenticationFlowContext context) {
        return ConfigHelper.getOtpCodeLength(context.getAuthenticatorConfig());
    }

    // IP Trust settings

    public static boolean isIpTrustEnabled(AuthenticatorConfigModel config) {
        return ConfigHelper.getConfigBooleanValue(
            config,
            EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_IP_TRUST_ENABLED,
            EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_IP_TRUST_ENABLED
        );
    }

    public static boolean isIpTrustEnabled(AuthenticationFlowContext context) {
        return ConfigHelper.isIpTrustEnabled(context.getAuthenticatorConfig());
    }

    public static int getIpTrustDurationMinutes(AuthenticatorConfigModel config) {
        return ConfigHelper.getConfigIntValue(
            config,
            EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_IP_TRUST_DURATION,
            EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_IP_TRUST_DURATION
        );
    }

    public static int getIpTrustDurationMinutes(AuthenticationFlowContext context) {
        return ConfigHelper.getIpTrustDurationMinutes(context.getAuthenticatorConfig());
    }

    /**
     * Get IP trust duration in seconds.
     */
    public static long getIpTrustDurationSeconds(AuthenticationFlowContext context) {
        return getIpTrustDurationMinutes(context) * 60L;
    }

    // Device Trust settings

    public static boolean isDeviceTrustEnabled(AuthenticatorConfigModel config) {
        return ConfigHelper.getConfigBooleanValue(
            config,
            EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_DEVICE_TRUST_ENABLED,
            EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_DEVICE_TRUST_ENABLED
        );
    }

    public static boolean isDeviceTrustEnabled(AuthenticationFlowContext context) {
        return ConfigHelper.isDeviceTrustEnabled(context.getAuthenticatorConfig());
    }

    public static int getDeviceTrustDurationDays(AuthenticatorConfigModel config) {
        return ConfigHelper.getConfigIntValue(
            config,
            EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_DEVICE_TRUST_DURATION,
            EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_DEVICE_TRUST_DURATION
        );
    }

    public static int getDeviceTrustDurationDays(AuthenticationFlowContext context) {
        return ConfigHelper.getDeviceTrustDurationDays(context.getAuthenticatorConfig());
    }

    /**
     * Get device trust duration in seconds.
     * Returns 0 if permanent (never expires).
     */
    public static long getDeviceTrustDurationSeconds(AuthenticationFlowContext context) {
        int days = getDeviceTrustDurationDays(context);
        if (days == 0) {
            return 0; // permanent
        }
        return days * 86400L; // days to seconds
    }

    // Trust behavior settings

    public static boolean isTrustOnlyWhenSole(AuthenticatorConfigModel config) {
        return ConfigHelper.getConfigBooleanValue(
            config,
            EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_TRUST_ONLY_WHEN_SOLE,
            EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_TRUST_ONLY_WHEN_SOLE
        );
    }

    public static boolean isTrustOnlyWhenSole(AuthenticationFlowContext context) {
        return ConfigHelper.isTrustOnlyWhenSole(context.getAuthenticatorConfig());
    }

    // Issuance limit settings

    public static int getResendCooldownSeconds(AuthenticatorConfigModel config) {
        return ConfigHelper.getIssuanceSettingIntValue(
            config,
            EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_RESEND_COOLDOWN,
            EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_RESEND_COOLDOWN,
            Integer.MAX_VALUE
        );
    }

    public static int getResendCooldownSeconds(AuthenticationFlowContext context) {
        return ConfigHelper.getResendCooldownSeconds(context.getAuthenticatorConfig());
    }

    public static int getIssuanceLimit(AuthenticatorConfigModel config) {
        return ConfigHelper.getIssuanceSettingIntValue(
            config,
            EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_ISSUANCE_LIMIT,
            EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_ISSUANCE_LIMIT,
            IssuanceLimiter.MAX_LIMIT
        );
    }

    public static int getIssuanceLimit(AuthenticationFlowContext context) {
        return ConfigHelper.getIssuanceLimit(context.getAuthenticatorConfig());
    }

    public static int getIssuanceLimitWindowSeconds(AuthenticatorConfigModel config) {
        return ConfigHelper.getIssuanceSettingIntValue(
            config,
            EmailOTPFormAuthenticatorFactory.SETTINGS_KEY_ISSUANCE_LIMIT_WINDOW,
            EmailOTPFormAuthenticatorFactory.SETTINGS_DEFAULT_VALUE_ISSUANCE_LIMIT_WINDOW,
            Integer.MAX_VALUE
        );
    }

    public static int getIssuanceLimitWindowSeconds(AuthenticationFlowContext context) {
        return ConfigHelper.getIssuanceLimitWindowSeconds(context.getAuthenticatorConfig());
    }

    /**
     * Like getConfigIntValue, but ignores surrounding whitespace and warns once about values that
     * are unparseable, negative or above {@code max}: for these settings a mistake would otherwise
     * silently switch the protection off or change it.
     */
    private static int getIssuanceSettingIntValue(AuthenticatorConfigModel config, String key, int defaultValue, int max) {
        if (null == config || !config.getConfig().containsKey(key)) {
            return defaultValue;
        }

        String value = config.getConfig().get(key);
        if (null == value || value.trim().isEmpty()) {
            return defaultValue;
        }

        int number;
        try {
            number = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            if (reportedValues.add(key + "=" + value)) {
                logger.warnf("Invalid number '%s' for email OTP setting '%s', using the default %d", value, key, defaultValue);
            }
            return defaultValue;
        }

        if (number < 0 && reportedValues.add(key + "=" + value)) {
            logger.warnf("Negative value %d for email OTP setting '%s' disables it", number, key);
        } else if (number > max && reportedValues.add(key + "=" + value)) {
            logger.warnf("Value %d for email OTP setting '%s' is above the maximum of %d, using %d", number, key, max, max);
        }

        return number;
    }

    public static String getConfigStringValue(AuthenticatorConfigModel config, String key) {
        return getConfigStringValue(config, key, null);
    }

    public static String getConfigStringValue(AuthenticatorConfigModel config, String key, String defaultValue) {
        if (null == config || !config.getConfig().containsKey(key)) {
            return defaultValue;
        }

        String value = config.getConfig().get(key);
        if (null == value || value.isEmpty()) {
            return defaultValue;
        }

        return value;
    }

    public static int getConfigIntValue(AuthenticatorConfigModel config, String key) {
        return getConfigIntValue(config, key, 0);
    }

    public static int getConfigIntValue(AuthenticatorConfigModel config, String key, int defaultValue) {
        if (null == config || !config.getConfig().containsKey(key)) {
            return defaultValue;
        }

        String value = config.getConfig().get(key);
        if (null == value || value.isEmpty()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }


    public static boolean getConfigBooleanValue(AuthenticatorConfigModel config, String key) {
        return getConfigBooleanValue(config, key, false);
    }

    public static boolean getConfigBooleanValue(AuthenticatorConfigModel config, String key, boolean defaultValue) {
        if (null == config || !config.getConfig().containsKey(key)) {
            return defaultValue;
        }

        return Boolean.parseBoolean(
            config.getConfig().get(key)
        );
    }
}
