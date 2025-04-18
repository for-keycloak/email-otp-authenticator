package ch.jacem.for_keycloak.email_otp_authenticator;

import java.util.Arrays;
import java.util.List;

import org.keycloak.Config.Scope;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

public class EmailOTPFormAuthenticatorFactory implements AuthenticatorFactory {
    public final static String PROVIDER_ID = "email-otp-form";
    private final static EmailOTPFormAuthenticator SINGLETON = new EmailOTPFormAuthenticator();

    public static final String SETTINGS_KEY_USER_ROLE = "user-role";
    public static final String SETTINGS_DEFAULT_VALUE_USER_ROLE = null;
    public static final String SETTINGS_KEY_NEGATE_USER_ROLE = "negate-user-role";
    public static final boolean SETTINGS_DEFAULT_VALUE_NEGATE_USER_ROLE = false;
    public static final String SETTINGS_KEY_CODE_ALPHABET = "code-alphabet";
    public static final String SETTINGS_DEFAULT_VALUE_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // Removed 0, 1, I, O to avoid confusion
    public static final String SETTINGS_KEY_CODE_LENGTH = "code-length";
    public static final int SETTINGS_DEFAULT_VALUE_CODE_LENGTH = 6;
    public static final String SETTINGS_KEY_CODE_LIFETIME = "code-lifetime";
    public static final int SETTINGS_DEFAULT_VALUE_CODE_LIFETIME = 600; // 10 minutes

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public void init(Scope config) {}

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getReferenceCategory() {
        return "otp-over-email";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public String getDisplayType() {
        return "Email OTP Form";
    }

    @Override
    public String getHelpText() {
        return "Validates a OTP sent over email on a separate OTP form.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return Arrays.asList(
            new ProviderConfigProperty(
                SETTINGS_KEY_USER_ROLE,
                "User Role",
                "The OTP will only be required for users with this role. Leave empty to require OTP for all users. Check the 'Negate User Role' option to require OTP for all users except those with this role.",
                ProviderConfigProperty.ROLE_TYPE,
                SETTINGS_DEFAULT_VALUE_USER_ROLE
            ),
            new ProviderConfigProperty(
                SETTINGS_KEY_NEGATE_USER_ROLE,
                "Negate User Role",
                "If checked, the OTP will be required for all users except those with the specified role.",
                ProviderConfigProperty.BOOLEAN_TYPE,
                String.valueOf(SETTINGS_DEFAULT_VALUE_NEGATE_USER_ROLE)
            ),
            new ProviderConfigProperty(
                SETTINGS_KEY_CODE_ALPHABET,
                "Code Alphabet",
                "The alphabet used to generate the code.",
                ProviderConfigProperty.STRING_TYPE,
                SETTINGS_DEFAULT_VALUE_CODE_ALPHABET
            ),
            new ProviderConfigProperty(
                SETTINGS_KEY_CODE_LENGTH,
                "Code Length",
                "The length of the generated code.",
                // Using STRING_TYPE to keep compatibility with older versions of Keycloak, need to cast this to int
                ProviderConfigProperty.STRING_TYPE,
                String.valueOf(SETTINGS_DEFAULT_VALUE_CODE_LENGTH)
            ),
            new ProviderConfigProperty(
                SETTINGS_KEY_CODE_LIFETIME,
                "Code Lifetime",
                "The number of seconds the code would remain valid (Default: 600s = 10min).",
                // Using STRING_TYPE to keep compatibility with older versions of Keycloak, need to cast this to int
                ProviderConfigProperty.STRING_TYPE,
                String.valueOf(SETTINGS_DEFAULT_VALUE_CODE_LIFETIME)
            )
        );
    }
}
