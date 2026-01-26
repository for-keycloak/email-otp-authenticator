package ch.jacem.for_keycloak.email_otp_authenticator.helpers;

/**
 * CLDR-based plural category selection for internationalization.
 * Returns the appropriate plural form ("One", "Two", "Few", or "Many") based on language rules.
 *
 * @see <a href="https://cldr.unicode.org/index/cldr-spec/plural-rules">CLDR Plural Rules</a>
 */
public final class PluralRules {

    private PluralRules() {
        // Utility class
    }

    /**
     * Get the plural category for a given number and language.
     *
     * @param n    The number to get the plural category for
     * @param lang The language code (e.g., "en", "ar", "ru")
     * @return The plural category: "One", "Two", "Few", or "Many"
     */
    public static String getCategory(int n, String lang) {
        if (lang == null) {
            lang = "en";
        }

        if ("ar".equals(lang)) {
            return arabicPlural(n);
        } else if ("sl".equals(lang)) {
            return slovenianPlural(n);
        } else if ("ru".equals(lang) || "uk".equals(lang) || "hr".equals(lang)) {
            return slavicPlural(n);
        } else if ("pl".equals(lang)) {
            return polishPlural(n);
        } else if ("cs".equals(lang) || "sk".equals(lang)) {
            return czechSlovakPlural(n);
        } else if ("lt".equals(lang)) {
            return lithuanianPlural(n);
        } else if ("ro".equals(lang)) {
            return romanianPlural(n);
        } else {
            return defaultPlural(n);
        }
    }

    // Arabic: one, two, few (3-10), many (11+)
    private static String arabicPlural(int n) {
        if (n == 1) return "One";
        if (n == 2) return "Two";
        if (n >= 3 && n <= 10) return "Few";
        return "Many";
    }

    // Slovenian: one (x01), two (x02), few (x03-x04), many (rest)
    private static String slovenianPlural(int n) {
        int mod100 = n % 100;
        if (mod100 == 1) return "One";
        if (mod100 == 2) return "Two";
        if (mod100 == 3 || mod100 == 4) return "Few";
        return "Many";
    }

    // Russian/Ukrainian/Croatian: one (1,21,31), few (2-4,22-24), many (5-20,25-30)
    private static String slavicPlural(int n) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return "One";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "Few";
        return "Many";
    }

    // Polish: one (1), few (2-4,22-24), many (5-21,25-31)
    private static String polishPlural(int n) {
        if (n == 1) return "One";
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "Few";
        return "Many";
    }

    // Czech/Slovak: one (1), few (2-4), many (5+)
    private static String czechSlovakPlural(int n) {
        if (n == 1) return "One";
        if (n >= 2 && n <= 4) return "Few";
        return "Many";
    }

    // Lithuanian: one (1,21,31), few (2-9,22-29), many (10-20,30)
    private static String lithuanianPlural(int n) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return "One";
        if (mod10 >= 2 && (mod100 < 10 || mod100 >= 20)) return "Few";
        return "Many";
    }

    // Romanian: one (1), few (2-19, 101-119), many (20+)
    private static String romanianPlural(int n) {
        if (n == 1) return "One";
        int mod100 = n % 100;
        if (n == 0 || (mod100 >= 1 && mod100 <= 19)) return "Few";
        return "Many";
    }

    // Default (English, German, etc.): singular/plural
    private static String defaultPlural(int n) {
        return n == 1 ? "One" : "Many";
    }
}
