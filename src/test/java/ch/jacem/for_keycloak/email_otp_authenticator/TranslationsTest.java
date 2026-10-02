package ch.jacem.for_keycloak.email_otp_authenticator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import ch.jacem.for_keycloak.email_otp_authenticator.helpers.PluralRules;

@DisplayName("Translations")
class TranslationsTest {

    private static final String MESSAGES_PATH = "theme-resources/messages/messages_%s.properties";

    private static final List<String> REQUIRED_KEYS = Arrays.asList(
            // Form
            "loginEmailOtp",
            "doResendEmail",
            "errorInvalidEmailOtp",
            "errorExpiredEmailOtp",
            // Email
            "emailOtpSubject",
            "emailOtpYourAccessCode",
            "emailOtpExpiration",
            // Authenticator metadata
            "email-otp-form-display-name",
            "email-otp-form-help-text",
            // Device Trust
            "dontAskForCodePermanently",
            "dontAskForCodeFor",
            // Time units (minimum: One and Many forms)
            "unitDayOne",
            "unitDayMany",
            "unitWeekOne",
            "unitWeekMany",
            "unitMonthOne",
            "unitMonthMany",
            "unitYearOne",
            "unitYearMany",
            // Issuance limits (minimum: One and Many forms)
            "errorResendCooldownEmailOtpOne",
            "errorResendCooldownEmailOtpMany",
            "errorTooManyRequestsEmailOtp"
    );

    private static final List<String> DEVICE_TRUST_KEYS = Arrays.asList(
            "dontAskForCodePermanently",
            "dontAskForCodeFor"
    );

    private static final List<String> KEYS_WITH_PLACEHOLDERS = Arrays.asList(
            "emailOtpExpiration",      // {0} for minutes
            "dontAskForCodeFor"        // {0} for value, {1} for unit
    );

    private static final String RESEND_COOLDOWN_KEY_PREFIX = "errorResendCooldownEmailOtp";

    static Stream<String> allLocales() {
        return Stream.of(
                "en", "ar", "ca", "cs", "da", "de", "el", "es", "fa", "fi",
                "fr", "hr", "hu", "it", "ja", "ka", "ko", "lt", "nl", "no",
                "pl", "pt", "pt_BR", "ro", "ru", "sk", "sl", "sv", "th", "tr",
                "uk", "zh_CN", "zh_TW"
        );
    }

    static Stream<Arguments> scriptsToVerify() {
        return Stream.of(
            Arguments.of("ar", "Arabic", (java.util.function.IntPredicate) cp -> cp >= 0x0600 && cp <= 0x06FF),
            Arguments.of("ja", "Japanese", (java.util.function.IntPredicate) cp ->
                (cp >= 0x3040 && cp <= 0x309F) ||  // Hiragana
                (cp >= 0x30A0 && cp <= 0x30FF) ||  // Katakana
                (cp >= 0x4E00 && cp <= 0x9FFF)),   // Kanji
            Arguments.of("ru", "Cyrillic", (java.util.function.IntPredicate) cp -> cp >= 0x0400 && cp <= 0x04FF),
            Arguments.of("uk", "Cyrillic", (java.util.function.IntPredicate) cp -> cp >= 0x0400 && cp <= 0x04FF)
        );
    }

    private Properties loadMessages(String locale) throws IOException {
        String path = String.format(MESSAGES_PATH, locale);
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(is, "Messages file not found: " + path);
            props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
        }
        return props;
    }

    @Nested
    @DisplayName("All Locales")
    class AllLocales {

        @ParameterizedTest(name = "{0}")
        @MethodSource("ch.jacem.for_keycloak.email_otp_authenticator.TranslationsTest#allLocales")
        @DisplayName("has all required keys")
        void hasAllRequiredKeys(String locale) throws IOException {
            Properties messages = loadMessages(locale);

            for (String key : REQUIRED_KEYS) {
                String value = messages.getProperty(key);
                assertNotNull(value,
                    String.format("Missing key '%s' in locale '%s'", key, locale));
                assertFalse(value.isBlank(),
                    String.format("Empty value for key '%s' in locale '%s'", key, locale));
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("ch.jacem.for_keycloak.email_otp_authenticator.TranslationsTest#allLocales")
        @DisplayName("preserves placeholders in parameterized strings")
        void preservesPlaceholders(String locale) throws IOException {
            Properties messages = loadMessages(locale);

            for (String key : KEYS_WITH_PLACEHOLDERS) {
                String value = messages.getProperty(key);
                assertNotNull(value, String.format("Missing key '%s' in locale '%s'", key, locale));
                assertTrue(value.contains("{0}"),
                    String.format("Missing {0} placeholder in '%s' for locale '%s': %s", key, locale, value));
            }

            // dontAskForCodeFor requires both {0} and {1}
            String dontAskFor = messages.getProperty("dontAskForCodeFor");
            assertTrue(dontAskFor.contains("{1}"),
                String.format("Missing {1} placeholder in 'dontAskForCodeFor' for locale '%s': %s", locale, dontAskFor));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("ch.jacem.for_keycloak.email_otp_authenticator.TranslationsTest#allLocales")
        @DisplayName("escapes apostrophes for MessageFormat")
        void escapesApostrophes(String locale) throws IOException {
            Properties messages = loadMessages(locale);

            // Keycloak renders every message through MessageFormat, where a single ' starts a
            // quoted section and is dropped: "d'accès" would render as "daccès"
            for (String key : messages.stringPropertyNames()) {
                String value = messages.getProperty(key);
                assertFalse(value.replace("''", "").contains("'"),
                    String.format("Unescaped apostrophe in '%s' for locale '%s', write it as '': %s", key, locale, value));
            }
        }
    }

    @Nested
    @DisplayName("Issuance Limit Translations")
    class IssuanceLimitTranslations {

        @ParameterizedTest(name = "{0}")
        @MethodSource("ch.jacem.for_keycloak.email_otp_authenticator.TranslationsTest#allLocales")
        @DisplayName("has a resend cooldown message for every plural form the language uses")
        void hasEveryPluralFormOfResendCooldown(String locale) throws IOException {
            Properties messages = loadMessages(locale);
            String lang = locale.split("_")[0];

            Set<String> categories = new TreeSet<>();
            for (int seconds = 1; seconds <= 3600; seconds++) {
                categories.add(PluralRules.getCategory(seconds, lang));
            }

            for (String category : categories) {
                String key = RESEND_COOLDOWN_KEY_PREFIX + category;
                String value = messages.getProperty(key);
                assertNotNull(value,
                    String.format("Missing key '%s' in locale '%s'", key, locale));
                assertFalse(value.isBlank(),
                    String.format("Empty value for key '%s' in locale '%s'", key, locale));
                // {0,number,#} keeps the locale's digits but never groups them (3600, not 3,600)
                assertTrue(value.contains("{0,number,#}"),
                    String.format("Missing {0,number,#} placeholder in '%s' for locale '%s': %s", key, locale, value));
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("ch.jacem.for_keycloak.email_otp_authenticator.TranslationsTest#allLocales")
        @DisplayName("issuance limit messages render through MessageFormat")
        void issuanceLimitMessagesRender(String locale) throws IOException {
            Properties messages = loadMessages(locale);
            Locale javaLocale = Locale.forLanguageTag(locale.replace('_', '-'));

            for (String key : messages.stringPropertyNames()) {
                if (!key.startsWith(RESEND_COOLDOWN_KEY_PREFIX) && !key.equals("errorTooManyRequestsEmailOtp")) {
                    continue;
                }
                String pattern = messages.getProperty(key);
                String rendered = new MessageFormat(pattern, javaLocale).format(new Object[] {3600L});
                String expectedNumber = new MessageFormat("{0,number,#}", javaLocale).format(new Object[] {3600L});

                assertFalse(rendered.contains("{"),
                    String.format("Unrendered placeholder in '%s' for locale '%s': %s", key, locale, rendered));
                if (key.startsWith(RESEND_COOLDOWN_KEY_PREFIX)) {
                    assertTrue(rendered.contains(expectedNumber),
                        String.format("Seconds not rendered ungrouped in '%s' for locale '%s': %s", key, locale, rendered));
                }
                // A lone apostrophe is a MessageFormat quote and would be dropped; write it as ''
                assertFalse(pattern.replace("''", "").contains("'"),
                    String.format("Apostrophes in '%s' for locale '%s' must be written as '': %s", key, locale, pattern));
            }
        }
    }

    @Nested
    @DisplayName("Device Trust Translations")
    class DeviceTrustTranslations {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"ar", "ja", "zh_CN", "ru", "fr", "de", "es"})
        @DisplayName("non-English locales have translated Device Trust strings")
        void deviceTrustStringsAreTranslated(String locale) throws IOException {
            Properties english = loadMessages("en");
            Properties translated = loadMessages(locale);

            for (String key : DEVICE_TRUST_KEYS) {
                String englishValue = english.getProperty(key);
                String translatedValue = translated.getProperty(key);

                assertNotNull(translatedValue,
                    String.format("Missing Device Trust key '%s' in locale '%s'", key, locale));
                assertNotEquals(englishValue, translatedValue,
                    String.format("Device Trust key '%s' not translated in locale '%s'", key, locale));
            }
        }
    }

    @Nested
    @DisplayName("Script Verification")
    class ScriptVerification {

        @ParameterizedTest(name = "{0} contains {1} characters")
        @MethodSource("ch.jacem.for_keycloak.email_otp_authenticator.TranslationsTest#scriptsToVerify")
        @DisplayName("locale contains expected script characters")
        void containsExpectedScript(String locale, String scriptName, java.util.function.IntPredicate hasScript) throws IOException {
            Properties messages = loadMessages(locale);

            for (String key : REQUIRED_KEYS) {
                String value = messages.getProperty(key);
                assertNotNull(value, "Missing key: " + key);

                boolean hasExpectedScript = value.codePoints().anyMatch(hasScript);
                assertTrue(hasExpectedScript,
                    String.format("Key '%s' in locale '%s' should contain %s characters: %s", key, locale, scriptName, value));
            }
        }

        @Test
        @DisplayName("Japanese Device Trust strings don't contain English")
        void japaneseDeviceTrustNoEnglish() throws IOException {
            Properties japanese = loadMessages("ja");

            for (String key : DEVICE_TRUST_KEYS) {
                String value = japanese.getProperty(key);
                assertFalse(value.contains("Don't"),
                    String.format("Key '%s' should not contain English: %s", key, value));
                assertFalse(value.contains("device"),
                    String.format("Key '%s' should not contain English: %s", key, value));
            }
        }
    }
}
