package ch.jacem.for_keycloak.email_otp_authenticator.helpers;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("PluralRules")
class PluralRulesTest {

    @Nested
    @DisplayName("Default (English-like) languages")
    class DefaultLanguages {

        @ParameterizedTest(name = "n={0} in {1} -> {2}")
        @CsvSource({
            "1, en, One",
            "2, en, Many",
            "5, en, Many",
            "1, de, One",
            "2, de, Many",
            "1, fr, One",
            "10, fr, Many"
        })
        void defaultPluralRules(int n, String lang, String expected) {
            assertEquals(expected, PluralRules.getCategory(n, lang));
        }

        @Test
        @DisplayName("null language uses default rules")
        void nullLanguage() {
            assertEquals("One", PluralRules.getCategory(1, null));
            assertEquals("Many", PluralRules.getCategory(2, null));
        }
    }

    @Nested
    @DisplayName("Arabic")
    class Arabic {

        @ParameterizedTest(name = "n={0} -> {1}")
        @CsvSource({
            "1, One",
            "2, Two",
            "3, Few",
            "10, Few",
            "11, Many",
            "100, Many"
        })
        void arabicPluralRules(int n, String expected) {
            assertEquals(expected, PluralRules.getCategory(n, "ar"));
        }
    }

    @Nested
    @DisplayName("Slovenian")
    class Slovenian {

        @ParameterizedTest(name = "n={0} -> {1}")
        @CsvSource({
            "1, One",
            "101, One",
            "2, Two",
            "102, Two",
            "3, Few",
            "4, Few",
            "103, Few",
            "5, Many",
            "10, Many",
            "100, Many"
        })
        void slovenianPluralRules(int n, String expected) {
            assertEquals(expected, PluralRules.getCategory(n, "sl"));
        }
    }

    @Nested
    @DisplayName("Slavic (Russian, Ukrainian, Croatian)")
    class Slavic {

        @ParameterizedTest(name = "n={0} in {1} -> {2}")
        @CsvSource({
            "1, ru, One",
            "21, ru, One",
            "31, ru, One",
            "2, ru, Few",
            "3, ru, Few",
            "4, ru, Few",
            "22, ru, Few",
            "5, ru, Many",
            "11, ru, Many",
            "12, ru, Many",
            "14, ru, Many",
            "20, ru, Many",
            "1, uk, One",
            "21, uk, One",
            "2, uk, Few",
            "1, hr, One",
            "2, hr, Few"
        })
        void slavicPluralRules(int n, String lang, String expected) {
            assertEquals(expected, PluralRules.getCategory(n, lang));
        }
    }

    @Nested
    @DisplayName("Polish")
    class Polish {

        @ParameterizedTest(name = "n={0} -> {1}")
        @CsvSource({
            "1, One",
            "2, Few",
            "3, Few",
            "4, Few",
            "22, Few",
            "23, Few",
            "24, Few",
            "5, Many",
            "11, Many",
            "12, Many",
            "21, Many"
        })
        void polishPluralRules(int n, String expected) {
            assertEquals(expected, PluralRules.getCategory(n, "pl"));
        }
    }

    @Nested
    @DisplayName("Czech and Slovak")
    class CzechSlovak {

        @ParameterizedTest(name = "n={0} in {1} -> {2}")
        @CsvSource({
            "1, cs, One",
            "2, cs, Few",
            "3, cs, Few",
            "4, cs, Few",
            "5, cs, Many",
            "10, cs, Many",
            "1, sk, One",
            "2, sk, Few",
            "5, sk, Many"
        })
        void czechSlovakPluralRules(int n, String lang, String expected) {
            assertEquals(expected, PluralRules.getCategory(n, lang));
        }
    }

    @Nested
    @DisplayName("Lithuanian")
    class Lithuanian {

        @ParameterizedTest(name = "n={0} -> {1}")
        @CsvSource({
            "1, One",
            "21, One",
            "31, One",
            "2, Few",
            "9, Few",
            "22, Few",
            "29, Few",
            "10, Many",
            "11, Many",
            "19, Many",
            "20, Many"
        })
        void lithuanianPluralRules(int n, String expected) {
            assertEquals(expected, PluralRules.getCategory(n, "lt"));
        }
    }

    @Nested
    @DisplayName("Romanian")
    class Romanian {

        @ParameterizedTest(name = "n={0} -> {1}")
        @CsvSource({
            "1, One",
            "0, Few",
            "2, Few",
            "19, Few",
            "101, Few",
            "119, Few",
            "20, Many",
            "100, Many",
            "120, Many"
        })
        void romanianPluralRules(int n, String expected) {
            assertEquals(expected, PluralRules.getCategory(n, "ro"));
        }
    }
}
