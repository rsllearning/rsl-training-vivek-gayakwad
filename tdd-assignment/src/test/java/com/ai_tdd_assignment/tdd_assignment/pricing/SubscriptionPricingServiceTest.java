package com.ai_tdd_assignment.tdd_assignment.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test suite for {@link SubscriptionPricingService#calculateMonthlyPrice(SubscriptionTier, int, String)}.
 *
 * Assumptions made explicit for the implementer (no production code is provided by this suite):
 * - Base monthly rates: BASIC = $50.00, PRO = $150.00, ENTERPRISE = $500.00.
 * - Longevity discount tiers are mutually exclusive (not stacked): activeMonths in (12, 36] -> 10% off;
 *   activeMonths > 36 -> 25% off; activeMonths <= 12 -> no discount.
 * - Voucher codes are case-sensitive and must not contain leading/trailing whitespace; anything that is
 *   not null/blank and not an exact match for a known code ("SAVE20", "HALFPRICE") throws InvalidVoucherException.
 * - A null or blank/empty voucher code is treated as "no voucher" and must NOT throw.
 * - SAVE20 subtracts a flat $20.00 from the rate AFTER the longevity discount has been applied.
 * - HALFPRICE multiplies the (longevity-discounted) rate by 0.5.
 * - A null tier throws NullPointerException; a negative activeMonths throws IllegalArgumentException.
 * - Returned BigDecimal values are monetary amounts scaled to 2 decimal places.
 */
class SubscriptionPricingServiceTest {

    private final SubscriptionPricingService service = new SubscriptionPricingService();

    private static void assertMoneyEquals(BigDecimal expected, BigDecimal actual) {
        assertNotNull(actual, "Result must not be null");
        assertEquals(0, expected.compareTo(actual),
                () -> "Expected " + expected + " but was " + actual);
    }

    // ---------------------------------------------------------------------
    // Happy flow: base prices per tier, no discount, no voucher
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Base price happy flow (no discount, no voucher)")
    class BasePriceTests {

        @Test
        @DisplayName("BASIC tier at <=12 months with no voucher returns $50.00")
        void basicBasePrice() {
            BigDecimal result = service.calculateMonthlyPrice(SubscriptionTier.BASIC, 6, null);
            assertMoneyEquals(new BigDecimal("50.00"), result);
        }

        @Test
        @DisplayName("PRO tier at <=12 months with no voucher returns $150.00")
        void proBasePrice() {
            BigDecimal result = service.calculateMonthlyPrice(SubscriptionTier.PRO, 6, null);
            assertMoneyEquals(new BigDecimal("150.00"), result);
        }

        @Test
        @DisplayName("ENTERPRISE tier at <=12 months with no voucher returns $500.00")
        void enterpriseBasePrice() {
            BigDecimal result = service.calculateMonthlyPrice(SubscriptionTier.ENTERPRISE, 6, null);
            assertMoneyEquals(new BigDecimal("500.00"), result);
        }

        @Test
        @DisplayName("activeMonths = 0 (brand new account) applies no discount")
        void zeroMonthsIsBasePrice() {
            BigDecimal result = service.calculateMonthlyPrice(SubscriptionTier.BASIC, 0, null);
            assertMoneyEquals(new BigDecimal("50.00"), result);
        }
    }

    // ---------------------------------------------------------------------
    // Longevity discount boundaries
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Longevity discount thresholds")
    class LongevityDiscountTests {

        @Test
        @DisplayName("Exactly 12 months does NOT qualify for the 10% discount")
        void exactlyTwelveMonthsNoDiscount() {
            BigDecimal result = service.calculateMonthlyPrice(SubscriptionTier.BASIC, 12, null);
            assertMoneyEquals(new BigDecimal("50.00"), result);
        }

        @Test
        @DisplayName("13 months qualifies for the 10% discount")
        void thirteenMonthsGetsTenPercentOff() {
            BigDecimal result = service.calculateMonthlyPrice(SubscriptionTier.BASIC, 13, null);
            assertMoneyEquals(new BigDecimal("45.00"), result);
        }

        @Test
        @DisplayName("Exactly 36 months only gets the 10% discount, not 25%")
        void exactlyThirtySixMonthsGetsTenPercentOnly() {
            BigDecimal result = service.calculateMonthlyPrice(SubscriptionTier.BASIC, 36, null);
            assertMoneyEquals(new BigDecimal("45.00"), result);
        }

        @Test
        @DisplayName("37 months qualifies for the 25% discount")
        void thirtySevenMonthsGetsTwentyFivePercentOff() {
            BigDecimal result = service.calculateMonthlyPrice(SubscriptionTier.BASIC, 37, null);
            assertMoneyEquals(new BigDecimal("37.50"), result);
        }

        @ParameterizedTest(name = "{0} tier at 13 months -> 10% off base rate")
        @EnumSource(SubscriptionTier.class)
        @DisplayName("10% longevity discount applies uniformly across all tiers")
        void tenPercentDiscountAppliesToAllTiers(SubscriptionTier tier) {
            BigDecimal base = basePriceOf(tier);
            BigDecimal expected = base.multiply(new BigDecimal("0.90"));
            BigDecimal result = service.calculateMonthlyPrice(tier, 13, null);
            assertMoneyEquals(expected, result);
        }

        @ParameterizedTest(name = "{0} tier at 37 months -> 25% off base rate")
        @EnumSource(SubscriptionTier.class)
        @DisplayName("25% longevity discount applies uniformly across all tiers")
        void twentyFivePercentDiscountAppliesToAllTiers(SubscriptionTier tier) {
            BigDecimal base = basePriceOf(tier);
            BigDecimal expected = base.multiply(new BigDecimal("0.75"));
            BigDecimal result = service.calculateMonthlyPrice(tier, 37, null);
            assertMoneyEquals(expected, result);
        }

        private BigDecimal basePriceOf(SubscriptionTier tier) {
            return switch (tier) {
                case BASIC -> new BigDecimal("50.00");
                case PRO -> new BigDecimal("150.00");
                case ENTERPRISE -> new BigDecimal("500.00");
            };
        }
    }

    // ---------------------------------------------------------------------
    // Voucher + longevity discount combination matrix
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Voucher and longevity discount combinations")
    class VoucherCombinationTests {

        @ParameterizedTest(name = "{0} tier, {1} months, voucher=''{2}'' -> ${3}")
        @MethodSource("com.ai_tdd_assignment.tdd_assignment.pricing.SubscriptionPricingServiceTest#pricingMatrix")
        @DisplayName("Full pricing matrix: tier x longevity tier x voucher")
        void pricingMatrixIsCorrect(SubscriptionTier tier, int months, String voucherCode, BigDecimal expected) {
            BigDecimal result = service.calculateMonthlyPrice(tier, months, voucherCode);
            assertMoneyEquals(expected, result);
        }
    }

    static Stream<Arguments> pricingMatrix() {
        return Stream.of(
                // ---- BASIC ($50.00 base) ----
                Arguments.of(SubscriptionTier.BASIC, 0, null, new BigDecimal("50.00")),
                Arguments.of(SubscriptionTier.BASIC, 0, "SAVE20", new BigDecimal("30.00")),
                Arguments.of(SubscriptionTier.BASIC, 0, "HALFPRICE", new BigDecimal("25.00")),
                Arguments.of(SubscriptionTier.BASIC, 13, null, new BigDecimal("45.00")),
                Arguments.of(SubscriptionTier.BASIC, 13, "SAVE20", new BigDecimal("25.00")),
                Arguments.of(SubscriptionTier.BASIC, 13, "HALFPRICE", new BigDecimal("22.50")),
                Arguments.of(SubscriptionTier.BASIC, 37, null, new BigDecimal("37.50")),
                Arguments.of(SubscriptionTier.BASIC, 37, "SAVE20", new BigDecimal("17.50")),
                Arguments.of(SubscriptionTier.BASIC, 37, "HALFPRICE", new BigDecimal("18.75")),

                // ---- PRO ($150.00 base) ----
                Arguments.of(SubscriptionTier.PRO, 0, null, new BigDecimal("150.00")),
                Arguments.of(SubscriptionTier.PRO, 0, "SAVE20", new BigDecimal("130.00")),
                Arguments.of(SubscriptionTier.PRO, 0, "HALFPRICE", new BigDecimal("75.00")),
                Arguments.of(SubscriptionTier.PRO, 13, null, new BigDecimal("135.00")),
                Arguments.of(SubscriptionTier.PRO, 13, "SAVE20", new BigDecimal("115.00")),
                Arguments.of(SubscriptionTier.PRO, 13, "HALFPRICE", new BigDecimal("67.50")),
                Arguments.of(SubscriptionTier.PRO, 37, null, new BigDecimal("112.50")),
                Arguments.of(SubscriptionTier.PRO, 37, "SAVE20", new BigDecimal("92.50")),
                Arguments.of(SubscriptionTier.PRO, 37, "HALFPRICE", new BigDecimal("56.25")),

                // ---- ENTERPRISE ($500.00 base) ----
                Arguments.of(SubscriptionTier.ENTERPRISE, 0, null, new BigDecimal("500.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 0, "SAVE20", new BigDecimal("480.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 0, "HALFPRICE", new BigDecimal("250.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 13, null, new BigDecimal("450.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 13, "SAVE20", new BigDecimal("430.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 13, "HALFPRICE", new BigDecimal("225.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 37, null, new BigDecimal("375.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 37, "SAVE20", new BigDecimal("355.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 37, "HALFPRICE", new BigDecimal("187.50"))
        );
    }

    // ---------------------------------------------------------------------
    // Null / blank voucher handling (must NOT throw)
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Null and blank voucher codes are treated as 'no voucher'")
    class NullAndBlankVoucherTests {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("null and empty voucher codes do not throw and apply no voucher discount")
        void nullOrEmptyVoucherAppliesNoDiscount(String voucherCode) {
            BigDecimal result = service.calculateMonthlyPrice(SubscriptionTier.BASIC, 13, voucherCode);
            assertMoneyEquals(new BigDecimal("45.00"), result);
        }
    }

    // ---------------------------------------------------------------------
    // Invalid input handling
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Invalid input handling")
    class InvalidInputTests {

        @Test
        @DisplayName("null tier throws NullPointerException")
        void nullTierThrowsNullPointerException() {
            assertThrows(NullPointerException.class,
                    () -> service.calculateMonthlyPrice(null, 5, null));
        }

        @Test
        @DisplayName("negative activeMonths throws IllegalArgumentException")
        void negativeActiveMonthsThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.calculateMonthlyPrice(SubscriptionTier.BASIC, -1, null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"FAKE10", "save20", "halfprice", "SAVE-20", "SAVE20 ", " SAVE20", "20SAVE"})
        @DisplayName("Unknown or malformed voucher codes throw InvalidVoucherException")
        void malformedOrUnknownVoucherThrowsInvalidVoucherException(String voucherCode) {
            assertThrows(InvalidVoucherException.class,
                    () -> service.calculateMonthlyPrice(SubscriptionTier.BASIC, 5, voucherCode));
        }
    }

    // ---------------------------------------------------------------------
    // Boundary / precision assertions
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Boundary and precision assertions")
    class BoundaryAndPrecisionTests {

        @Test
        @DisplayName("Integer.MAX_VALUE months does not overflow and applies the 25% longevity tier")
        void maxIntMonthsAppliesTopLongevityTierWithoutOverflow() {
            BigDecimal result = service.calculateMonthlyPrice(SubscriptionTier.BASIC, Integer.MAX_VALUE, null);
            assertMoneyEquals(new BigDecimal("37.50"), result);
        }

        @Test
        @DisplayName("Integer.MAX_VALUE months combined with HALFPRICE voucher resolves correctly")
        void maxIntMonthsWithHalfPriceVoucher() {
            BigDecimal result = service.calculateMonthlyPrice(SubscriptionTier.ENTERPRISE, Integer.MAX_VALUE, "HALFPRICE");
            assertMoneyEquals(new BigDecimal("187.50"), result);
        }

        @Test
        @DisplayName("Returned monetary amount is scaled to exactly 2 decimal places")
        void resultIsScaledToTwoDecimalPlaces() {
            BigDecimal result = service.calculateMonthlyPrice(SubscriptionTier.PRO, 37, "HALFPRICE");
            assertEquals(2, result.scale(), () -> "Expected scale 2 but was " + result.scale());
        }

        @Test
        @DisplayName("SAVE20 on the lowest tier with maximum discount never produces a negative price")
        void save20NeverProducesNegativePrice() {
            BigDecimal result = service.calculateMonthlyPrice(SubscriptionTier.BASIC, 37, "SAVE20");
            assertTrue(result.compareTo(BigDecimal.ZERO) >= 0,
                    () -> "Price must never be negative but was " + result);
        }
    }
}
