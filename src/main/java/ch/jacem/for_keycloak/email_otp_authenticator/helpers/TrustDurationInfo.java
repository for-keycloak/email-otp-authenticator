package ch.jacem.for_keycloak.email_otp_authenticator.helpers;

/**
 * Helper for formatting trust duration in the UI.
 * Converts days to appropriate units (days, weeks, months, years).
 */
public class TrustDurationInfo {

    private final int value;
    private final String unit;

    public TrustDurationInfo(int value, String unit) {
        this.value = value;
        this.unit = unit;
    }

    public int getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    /**
     * Get the message key for the unit with proper plural form.
     *
     * @param lang The language code (e.g., "en", "ar", "ru")
     * @return The message key (e.g., "unitDayOne", "unitDayMany")
     */
    public String getUnitMessageKey(String lang) {
        String category = PluralRules.getCategory(value, lang);
        return "unit" + capitalize(unit) + category;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Create from days, converting to the most readable unit.
     *
     * @param days Number of days (0 = permanent)
     * @return TrustDurationInfo or null if permanent
     */
    public static TrustDurationInfo fromDays(int days) {
        if (days == 0) {
            return null;
        }

        // Days
        if (days < 7) {
            return new TrustDurationInfo(days, "day");
        }

        // Weeks (only if exact weeks)
        if (days < 30 && days % 7 == 0) {
            int weeks = days / 7;
            return new TrustDurationInfo(weeks, "week");
        }
        if (days < 30) {
            return new TrustDurationInfo(days, "day");
        }

        // Months
        if (days < 365) {
            int months = Math.round(days / 30.0f);
            if (months == 0) months = 1;
            return new TrustDurationInfo(months, "month");
        }

        // Years
        int years = Math.round(days / 365.0f);
        if (years == 0) years = 1;
        return new TrustDurationInfo(years, "year");
    }
}
