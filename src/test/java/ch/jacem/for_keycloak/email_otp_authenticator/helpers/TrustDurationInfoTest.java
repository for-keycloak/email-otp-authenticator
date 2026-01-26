package ch.jacem.for_keycloak.email_otp_authenticator.helpers;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TrustDurationInfo")
class TrustDurationInfoTest {

    @Nested
    @DisplayName("fromDays")
    class FromDays {

        @Test
        @DisplayName("0 days returns null (permanent)")
        void zeroDaysReturnsNull() {
            assertNull(TrustDurationInfo.fromDays(0));
        }

        @Test
        @DisplayName("1 day returns singular day")
        void oneDayReturnsSingular() {
            TrustDurationInfo info = TrustDurationInfo.fromDays(1);

            assertEquals(1, info.getValue());
            assertEquals("day", info.getUnit());
        }

        @Test
        @DisplayName("2-6 days return plural days")
        void multipleDaysReturnPlural() {
            TrustDurationInfo info = TrustDurationInfo.fromDays(5);

            assertEquals(5, info.getValue());
            assertEquals("day", info.getUnit());
        }

        @Test
        @DisplayName("7 days returns 1 week")
        void sevenDaysReturnsOneWeek() {
            TrustDurationInfo info = TrustDurationInfo.fromDays(7);

            assertEquals(1, info.getValue());
            assertEquals("week", info.getUnit());
        }

        @Test
        @DisplayName("14 days returns 2 weeks")
        void fourteenDaysReturnsTwoWeeks() {
            TrustDurationInfo info = TrustDurationInfo.fromDays(14);

            assertEquals(2, info.getValue());
            assertEquals("week", info.getUnit());
        }

        @Test
        @DisplayName("21 days returns 3 weeks")
        void twentyOneDaysReturnsThreeWeeks() {
            TrustDurationInfo info = TrustDurationInfo.fromDays(21);

            assertEquals(3, info.getValue());
            assertEquals("week", info.getUnit());
        }

        @Test
        @DisplayName("10 days returns 10 days (not exact weeks)")
        void tenDaysReturnsDays() {
            TrustDurationInfo info = TrustDurationInfo.fromDays(10);

            assertEquals(10, info.getValue());
            assertEquals("day", info.getUnit());
        }

        @Test
        @DisplayName("30 days returns 1 month")
        void thirtyDaysReturnsOneMonth() {
            TrustDurationInfo info = TrustDurationInfo.fromDays(30);

            assertEquals(1, info.getValue());
            assertEquals("month", info.getUnit());
        }

        @Test
        @DisplayName("60 days returns 2 months")
        void sixtyDaysReturnsTwoMonths() {
            TrustDurationInfo info = TrustDurationInfo.fromDays(60);

            assertEquals(2, info.getValue());
            assertEquals("month", info.getUnit());
        }

        @Test
        @DisplayName("90 days returns 3 months")
        void ninetyDaysReturnsThreeMonths() {
            TrustDurationInfo info = TrustDurationInfo.fromDays(90);

            assertEquals(3, info.getValue());
            assertEquals("month", info.getUnit());
        }

        @Test
        @DisplayName("365 days returns 1 year")
        void yearReturnsSingular() {
            TrustDurationInfo info = TrustDurationInfo.fromDays(365);

            assertEquals(1, info.getValue());
            assertEquals("year", info.getUnit());
        }

        @Test
        @DisplayName("730 days returns 2 years")
        void twoYearsReturnsPlural() {
            TrustDurationInfo info = TrustDurationInfo.fromDays(730);

            assertEquals(2, info.getValue());
            assertEquals("year", info.getUnit());
        }

        @Test
        @DisplayName("400 days rounds to 1 year")
        void roundsToNearestYear() {
            TrustDurationInfo info = TrustDurationInfo.fromDays(400);

            assertEquals(1, info.getValue());
            assertEquals("year", info.getUnit());
        }

        @Test
        @DisplayName("45 days rounds to 2 months")
        void roundsToNearestMonth() {
            TrustDurationInfo info = TrustDurationInfo.fromDays(45);

            assertEquals(2, info.getValue());
            assertEquals("month", info.getUnit());
        }
    }

    @Nested
    @DisplayName("getUnitMessageKey")
    class GetUnitMessageKey {

        @Test
        @DisplayName("English singular returns One suffix")
        void englishSingular() {
            TrustDurationInfo info = new TrustDurationInfo(1, "day");
            assertEquals("unitDayOne", info.getUnitMessageKey("en"));
        }

        @Test
        @DisplayName("English plural returns Many suffix")
        void englishPlural() {
            TrustDurationInfo info = new TrustDurationInfo(5, "day");
            assertEquals("unitDayMany", info.getUnitMessageKey("en"));
        }

        @Test
        @DisplayName("null language defaults to English rules")
        void nullLanguageDefaultsToEnglish() {
            TrustDurationInfo info = new TrustDurationInfo(2, "week");
            assertEquals("unitWeekMany", info.getUnitMessageKey(null));
        }

        @Test
        @DisplayName("capitalizes unit name correctly")
        void capitalizesUnit() {
            TrustDurationInfo info = new TrustDurationInfo(1, "month");
            assertEquals("unitMonthOne", info.getUnitMessageKey("en"));
        }

        @Test
        @DisplayName("works with year unit")
        void worksWithYear() {
            TrustDurationInfo info = new TrustDurationInfo(3, "year");
            assertEquals("unitYearMany", info.getUnitMessageKey("en"));
        }
    }

    @Nested
    @DisplayName("Constructor and Getters")
    class ConstructorAndGetters {

        @Test
        @DisplayName("constructor sets value and unit correctly")
        void constructorSetsFields() {
            TrustDurationInfo info = new TrustDurationInfo(5, "day");

            assertEquals(5, info.getValue());
            assertEquals("day", info.getUnit());
        }
    }
}
