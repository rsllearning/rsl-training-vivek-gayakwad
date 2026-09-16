package com.ai_tdd_assignment.tdd_assignment.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for {@link SubscriptionPricingService#calculateMonthlyPrice(SubscriptionTier, int, String)}.
 *
 * <p>Base monthly rates: BASIC $50.00, PRO $150.00, ENTERPRISE $500.00.
 *
 * <p>Longevity tiers are mutually exclusive, never stacked:
 * <ul>
 *   <li>activeMonths &lt;= 12 -> full rate</li>
 *   <li>12 &lt; activeMonths &lt;= 36 -> 10% off</li>
 *   <li>activeMonths &gt; 36 -> 25% off</li>
 * </ul>
 *
 * <p>Vouchers apply <em>after</em> the longevity discount. SAVE20 deducts a flat $20.00;
 * HALFPRICE halves the rate. A null or blank code (empty, spaces, tabs) means "no voucher".
 * Non-blank input is matched exactly and is never trimmed, so " SAVE20" is invalid, not SAVE20.
 * An unrecognised non-blank code throws {@link InvalidVoucherException} whose message carries
 * the rejected code. A null tier throws {@link NullPointerException} with the exact message
 * "tier must not be null"; a negative activeMonths throws {@link IllegalArgumentException}
 * whose message names the parameter.
 *
 * <p>Every expected amount below is an independently computed literal, never re-derived from the
 * production rate table or multipliers, so a shared arithmetic misconception cannot hide.
 * Amounts are asserted with {@code assertEquals} on {@link BigDecimal}, whose equality is
 * scale-sensitive, pinning both the value and the 2-decimal monetary scale in one assertion.
 *
 * <p><strong>Known limits of this suite.</strong> Two parts of the contract are unobservable
 * through the public API and are therefore specified but not asserted:
 * <ul>
 *   <li>HALFPRICE ordering. Multiplication commutes, so 50.00 x 0.90 x 0.50 equals
 *       50.00 x 0.50 x 0.90. No input can distinguish the order. SAVE20 ordering <em>is</em>
 *       decidable and is pinned by {@code save20AppliesAfterLongevityDiscount}.</li>
 *   <li>Rounding mode. The fixed rates 50/150/500 against multipliers 0.90/0.75/0.50 land
 *       exactly on a cent in every reachable case, so no input produces a sub-cent fraction.
 *       Scale normalisation is asserted instead.</li>
 * </ul>
 * A negative result is likewise unreachable: the cheapest case is BASIC at 25% off less SAVE20,
 * which floors at $17.50. No price-floor behaviour is specified because none can be triggered.
 */
class SubscriptionPricingServiceTest {

    private final SubscriptionPricingService service = new SubscriptionPricingService();

    private static void assertMessageContains(Throwable thrown, String expectedFragment, String why) {
        String message = String.valueOf(thrown.getMessage());
        assertTrue(message.contains(expectedFragment),
                () -> why + " - expected the message to contain \"" + expectedFragment
                        + "\" but it was: " + message);
    }

    @Nested
    @DisplayName("Longevity discount thresholds")
    class LongevityThresholds {

        @Test
        @DisplayName("12 months is not 'more than 12', so the full rate stands")
        void exactlyTwelveMonthsNoDiscount() {
            assertEquals(new BigDecimal("50.00"),
                    service.calculateMonthlyPrice(SubscriptionTier.BASIC, 12, null));
        }

        @Test
        @DisplayName("13 months crosses the first threshold: 10% off")
        void thirteenMonthsGetsTenPercentOff() {
            assertEquals(new BigDecimal("45.00"),
                    service.calculateMonthlyPrice(SubscriptionTier.BASIC, 13, null));
        }

        @Test
        @DisplayName("36 months is not 'more than 36', so it keeps 10% and does not reach 25%")
        void exactlyThirtySixMonthsGetsTenPercentOnly() {
            assertEquals(new BigDecimal("45.00"),
                    service.calculateMonthlyPrice(SubscriptionTier.BASIC, 36, null));
        }

        @Test
        @DisplayName("37 months crosses the second threshold: 25% off")
        void thirtySevenMonthsGetsTwentyFivePercentOff() {
            assertEquals(new BigDecimal("37.50"),
                    service.calculateMonthlyPrice(SubscriptionTier.BASIC, 37, null));
        }

        @Test
        @DisplayName("Discount tiers replace one another rather than stacking")
        void tiersDoNotStack() {
            // Stacking 10% then 25% would yield 33.75; the tiers are exclusive, so 25% alone wins.
            assertEquals(new BigDecimal("37.50"),
                    service.calculateMonthlyPrice(SubscriptionTier.BASIC, 48, null));
        }
    }

    @Nested
    @DisplayName("Price matrix: tier x longevity x voucher")
    class PriceMatrix {

        @ParameterizedTest(name = "{0}, {1} months, voucher={2} -> ${3}")
        @MethodSource("com.ai_tdd_assignment.tdd_assignment.pricing.SubscriptionPricingServiceTest#pricingMatrix")
        void resolvesExactAmount(SubscriptionTier tier, int months, String voucherCode, BigDecimal expected) {
            assertEquals(expected, service.calculateMonthlyPrice(tier, months, voucherCode));
        }

        @Test
        @DisplayName("SAVE20 is deducted after the longevity discount, not before")
        void save20AppliesAfterLongevityDiscount() {
            // The discriminating case: (50.00 x 0.90) - 20.00 = 25.00, whereas deducting first
            // would give (50.00 - 20.00) x 0.90 = 27.00. This is the only ordering the API exposes.
            assertEquals(new BigDecimal("25.00"),
                    service.calculateMonthlyPrice(SubscriptionTier.BASIC, 13, "SAVE20"));
        }
    }

    static Stream<Arguments> pricingMatrix() {
        return Stream.of(
                // BASIC, $50.00 base
                Arguments.of(SubscriptionTier.BASIC, 0, null, new BigDecimal("50.00")),
                Arguments.of(SubscriptionTier.BASIC, 0, "SAVE20", new BigDecimal("30.00")),
                Arguments.of(SubscriptionTier.BASIC, 0, "HALFPRICE", new BigDecimal("25.00")),
                Arguments.of(SubscriptionTier.BASIC, 13, null, new BigDecimal("45.00")),
                Arguments.of(SubscriptionTier.BASIC, 13, "SAVE20", new BigDecimal("25.00")),
                Arguments.of(SubscriptionTier.BASIC, 13, "HALFPRICE", new BigDecimal("22.50")),
                Arguments.of(SubscriptionTier.BASIC, 37, null, new BigDecimal("37.50")),
                Arguments.of(SubscriptionTier.BASIC, 37, "SAVE20", new BigDecimal("17.50")),
                Arguments.of(SubscriptionTier.BASIC, 37, "HALFPRICE", new BigDecimal("18.75")),

                // PRO, $150.00 base
                Arguments.of(SubscriptionTier.PRO, 0, null, new BigDecimal("150.00")),
                Arguments.of(SubscriptionTier.PRO, 0, "SAVE20", new BigDecimal("130.00")),
                Arguments.of(SubscriptionTier.PRO, 0, "HALFPRICE", new BigDecimal("75.00")),
                Arguments.of(SubscriptionTier.PRO, 13, null, new BigDecimal("135.00")),
                Arguments.of(SubscriptionTier.PRO, 13, "SAVE20", new BigDecimal("115.00")),
                Arguments.of(SubscriptionTier.PRO, 13, "HALFPRICE", new BigDecimal("67.50")),
                Arguments.of(SubscriptionTier.PRO, 37, null, new BigDecimal("112.50")),
                Arguments.of(SubscriptionTier.PRO, 37, "SAVE20", new BigDecimal("92.50")),
                Arguments.of(SubscriptionTier.PRO, 37, "HALFPRICE", new BigDecimal("56.25")),

                // ENTERPRISE, $500.00 base
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

    @Nested
    @DisplayName("Blank voucher codes mean 'no voucher'")
    class BlankVoucherCodes {

        @ParameterizedTest(name = "voucher={0} leaves the longevity-discounted rate untouched")
        @NullSource
        @ValueSource(strings = {"", " ", "   ", "\t"})
        void blankVoucherIsIgnored(String voucherCode) {
            assertEquals(new BigDecimal("45.00"),
                    service.calculateMonthlyPrice(SubscriptionTier.BASIC, 13, voucherCode));
        }
    }

    @Nested
    @DisplayName("Error contracts")
    class ErrorContracts {

        @Test
        @DisplayName("Null tier fails on a deliberate guard, not an incidental dereference")
        void nullTierThrowsGuardedNpe() {
            NullPointerException thrown = assertThrows(NullPointerException.class,
                    () -> service.calculateMonthlyPrice(null, 5, null));

            // Must be exact, not a "contains" check: an unguarded dereference also throws NPE, and
            // the JVM's helpful message ("... because \"tier\" is null") would satisfy any looser
            // assertion. Only the exact string proves a deliberate requireNonNull was written.
            assertEquals("tier must not be null", thrown.getMessage());
        }

        @ParameterizedTest(name = "activeMonths={0} is rejected")
        @ValueSource(ints = {-1, -13, Integer.MIN_VALUE})
        void negativeMonthsRejected(int activeMonths) {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> service.calculateMonthlyPrice(SubscriptionTier.BASIC, activeMonths, null));

            assertMessageContains(thrown, "activeMonths",
                    "A negative tenure must be rejected with a message naming the parameter");
        }

        @ParameterizedTest(name = "unrecognised code {0} is rejected")
        @ValueSource(strings = {"FAKE10", "20SAVE", "SAVE-20", "DISCOUNT"})
        void unknownCodeIsRejected(String voucherCode) {
            assertRejectedVoucher(voucherCode);
        }

        @ParameterizedTest(name = "wrong-case code {0} is rejected")
        @ValueSource(strings = {"save20", "Save20", "halfprice", "HalfPrice"})
        void voucherMatchingIsCaseSensitive(String voucherCode) {
            assertRejectedVoucher(voucherCode);
        }

        @ParameterizedTest(name = "padded code {0} is rejected rather than trimmed")
        @ValueSource(strings = {" SAVE20", "SAVE20 ", " HALFPRICE ", "\tSAVE20"})
        void paddedCodeIsNotTrimmed(String voucherCode) {
            assertRejectedVoucher(voucherCode);
        }

        private void assertRejectedVoucher(String voucherCode) {
            InvalidVoucherException thrown = assertThrows(InvalidVoucherException.class,
                    () -> service.calculateMonthlyPrice(SubscriptionTier.BASIC, 5, voucherCode));

            assertMessageContains(thrown, voucherCode,
                    "A rejected voucher must surface the offending code for log diagnosis");
        }
    }

    @Nested
    @DisplayName("Bounds and precision")
    class BoundsAndPrecision {

        @Test
        @DisplayName("Integer.MAX_VALUE months resolves to the 25% tier without overflowing")
        void maxIntMonthsAppliesTopTier() {
            assertEquals(new BigDecimal("37.50"),
                    service.calculateMonthlyPrice(SubscriptionTier.BASIC, Integer.MAX_VALUE, null));
        }

        @Test
        @DisplayName("Integer.MAX_VALUE months combines with HALFPRICE without overflowing")
        void maxIntMonthsWithHalfPrice() {
            assertEquals(new BigDecimal("187.50"),
                    service.calculateMonthlyPrice(SubscriptionTier.ENTERPRISE, Integer.MAX_VALUE, "HALFPRICE"));
        }

        @Test
        @DisplayName("A whole-dollar amount is returned as 50.00, never as 50 or 50.0")
        void wholeDollarAmountKeepsTwoDecimalScale() {
            BigDecimal result = service.calculateMonthlyPrice(SubscriptionTier.BASIC, 6, null);

            assertEquals("50.00", result.toPlainString());
            assertEquals(2, result.scale());
        }

        @Test
        @DisplayName("Repeated invocation with identical arguments yields an identical result")
        void repeatedInvocationYieldsIdenticalResult() {
            BigDecimal first = service.calculateMonthlyPrice(SubscriptionTier.PRO, 37, "SAVE20");
            BigDecimal second = service.calculateMonthlyPrice(SubscriptionTier.PRO, 37, "SAVE20");

            assertEquals(new BigDecimal("92.50"), first);
            assertEquals(first, second);
        }
    }
}
