package com.ai_tdd_assignment.tdd_assignment.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adversarial probes for {@link SubscriptionPricingService}. These attack the stated requirements
 * from angles the contract suite does not: inputs nobody would type on purpose, invariants that
 * must survive future edits, and combinations the happy-path matrix skips.
 */
class SubscriptionPricingServiceAdversarialTest {

    private final SubscriptionPricingService service = new SubscriptionPricingService();

    // EC1 - a tier added to the enum tomorrow is priced by nobody, and no existing test notices.
    @ParameterizedTest(name = "{0} longevity ladder")
    @EnumSource(SubscriptionTier.class)
    @DisplayName("Every tier's price falls at each longevity threshold and keeps monetary scale")
    void everyTierHasADecreasingLongevityLadder(SubscriptionTier tier) {
        BigDecimal atTwelve = service.calculateMonthlyPrice(tier, 12, null);
        BigDecimal atThirteen = service.calculateMonthlyPrice(tier, 13, null);
        BigDecimal atThirtySix = service.calculateMonthlyPrice(tier, 36, null);
        BigDecimal atThirtySeven = service.calculateMonthlyPrice(tier, 37, null);

        assertAll(
                () -> assertTrue(atTwelve.compareTo(atThirteen) > 0,
                        () -> "crossing 12 months must reduce the price: " + atTwelve + " -> " + atThirteen),
                () -> assertEquals(atThirteen, atThirtySix,
                        "13 and 36 months sit in the same 10% band"),
                () -> assertTrue(atThirtySix.compareTo(atThirtySeven) > 0,
                        () -> "crossing 36 months must reduce the price: " + atThirtySix + " -> " + atThirtySeven),
                () -> assertTrue(atThirtySeven.signum() > 0,
                        () -> tier + " must still cost something at maximum tenure, was " + atThirtySeven),
                () -> assertEquals(2, atThirtySeven.scale(),
                        () -> "monetary scale must survive the discount, was " + atThirtySeven)
        );
    }

    // EC2 - the thresholds are pinned only on BASIC with no voucher, so a tier-specific or
    // voucher-specific off-by-one at 12/36 months would ship green.
    @ParameterizedTest(name = "{0}, {1} months, voucher={2} -> ${3}")
    @MethodSource("boundaryMonthGrid")
    @DisplayName("Every tier and voucher agrees on the exact threshold months")
    void boundaryMonthsHoldAcrossAllTiersAndVouchers(SubscriptionTier tier, int months,
                                                     String voucherCode, BigDecimal expected) {
        assertEquals(expected, service.calculateMonthlyPrice(tier, months, voucherCode));
    }

    static Stream<Arguments> boundaryMonthGrid() {
        return Stream.of(
                // BASIC - 12 and 36 are the last months of their band
                Arguments.of(SubscriptionTier.BASIC, 12, null, new BigDecimal("50.00")),
                Arguments.of(SubscriptionTier.BASIC, 12, "SAVE20", new BigDecimal("30.00")),
                Arguments.of(SubscriptionTier.BASIC, 12, "HALFPRICE", new BigDecimal("25.00")),
                Arguments.of(SubscriptionTier.BASIC, 13, null, new BigDecimal("45.00")),
                Arguments.of(SubscriptionTier.BASIC, 13, "SAVE20", new BigDecimal("25.00")),
                Arguments.of(SubscriptionTier.BASIC, 13, "HALFPRICE", new BigDecimal("22.50")),
                Arguments.of(SubscriptionTier.BASIC, 36, null, new BigDecimal("45.00")),
                Arguments.of(SubscriptionTier.BASIC, 36, "SAVE20", new BigDecimal("25.00")),
                Arguments.of(SubscriptionTier.BASIC, 36, "HALFPRICE", new BigDecimal("22.50")),
                Arguments.of(SubscriptionTier.BASIC, 37, null, new BigDecimal("37.50")),
                Arguments.of(SubscriptionTier.BASIC, 37, "SAVE20", new BigDecimal("17.50")),
                Arguments.of(SubscriptionTier.BASIC, 37, "HALFPRICE", new BigDecimal("18.75")),

                // PRO
                Arguments.of(SubscriptionTier.PRO, 12, null, new BigDecimal("150.00")),
                Arguments.of(SubscriptionTier.PRO, 12, "SAVE20", new BigDecimal("130.00")),
                Arguments.of(SubscriptionTier.PRO, 12, "HALFPRICE", new BigDecimal("75.00")),
                Arguments.of(SubscriptionTier.PRO, 13, null, new BigDecimal("135.00")),
                Arguments.of(SubscriptionTier.PRO, 13, "SAVE20", new BigDecimal("115.00")),
                Arguments.of(SubscriptionTier.PRO, 13, "HALFPRICE", new BigDecimal("67.50")),
                Arguments.of(SubscriptionTier.PRO, 36, null, new BigDecimal("135.00")),
                Arguments.of(SubscriptionTier.PRO, 36, "SAVE20", new BigDecimal("115.00")),
                Arguments.of(SubscriptionTier.PRO, 36, "HALFPRICE", new BigDecimal("67.50")),
                Arguments.of(SubscriptionTier.PRO, 37, null, new BigDecimal("112.50")),
                Arguments.of(SubscriptionTier.PRO, 37, "SAVE20", new BigDecimal("92.50")),
                Arguments.of(SubscriptionTier.PRO, 37, "HALFPRICE", new BigDecimal("56.25")),

                // ENTERPRISE
                Arguments.of(SubscriptionTier.ENTERPRISE, 12, null, new BigDecimal("500.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 12, "SAVE20", new BigDecimal("480.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 12, "HALFPRICE", new BigDecimal("250.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 13, null, new BigDecimal("450.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 13, "SAVE20", new BigDecimal("430.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 13, "HALFPRICE", new BigDecimal("225.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 36, null, new BigDecimal("450.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 36, "SAVE20", new BigDecimal("430.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 36, "HALFPRICE", new BigDecimal("225.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 37, null, new BigDecimal("375.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 37, "SAVE20", new BigDecimal("355.00")),
                Arguments.of(SubscriptionTier.ENTERPRISE, 37, "HALFPRICE", new BigDecimal("187.50"))
        );
    }

    // EC3 - the rejected code is echoed verbatim into the exception message, so whatever a caller
    // sends ends up in the log file exactly as written.
    @ParameterizedTest(name = "{0}")
    @MethodSource("hostileVoucherCodes")
    @DisplayName("A hostile voucher code is rejected without being echoed back verbatim")
    void hostileVoucherCodeIsNotEchoedIntoTheMessage(String label, String voucherCode) {
        InvalidVoucherException thrown = assertThrows(InvalidVoucherException.class,
                () -> service.calculateMonthlyPrice(SubscriptionTier.BASIC, 5, voucherCode));

        String message = String.valueOf(thrown.getMessage());
        assertAll(
                () -> assertFalse(message.contains("\n") || message.contains("\r"),
                        "a line break in the message lets a caller forge log lines"),
                () -> assertTrue(message.length() <= 200,
                        () -> "message must stay bounded for logging, was " + message.length() + " chars")
        );
    }

    static Stream<Arguments> hostileVoucherCodes() {
        return Stream.of(
                Arguments.of("line feed forges a log record", "SAVE20\nINFO user granted admin"),
                Arguments.of("CRLF forges a log record", "SAVE20\r\n2026-01-01 ERROR billing bypassed"),
                Arguments.of("null byte", "SAVE20 "),
                Arguments.of("one million characters", "X".repeat(1_000_000))
        );
    }

    // EC4 - SAVE20 is a flat deduction, so it does not scale with the tier the way a percentage does.
    @ParameterizedTest(name = "{0} with SAVE20 at maximum tenure")
    @EnumSource(SubscriptionTier.class)
    @DisplayName("A flat deduction never drives any tier to zero or below")
    void flatDeductionNeverReachesZero(SubscriptionTier tier) {
        BigDecimal price = service.calculateMonthlyPrice(tier, Integer.MAX_VALUE, "SAVE20");

        assertTrue(price.signum() > 0,
                () -> tier + " bills " + price + " after the flat $20 deduction at maximum tenure");
    }

    // EC5 - String.isBlank() excludes non-breaking spaces, so visually identical whitespace
    // takes two completely different code paths.
    @ParameterizedTest(name = "non-breaking whitespace U+{0} is rejected as a code")
    @ValueSource(strings = {" ", " ", " "})
    @DisplayName("Non-breaking whitespace is treated as a voucher code, not as blank")
    void nonBreakingWhitespaceIsRejected(String voucherCode) {
        assertThrows(InvalidVoucherException.class,
                () -> service.calculateMonthlyPrice(SubscriptionTier.BASIC, 13, voucherCode));
    }

    @ParameterizedTest(name = "ordinary whitespace is ignored")
    @ValueSource(strings = {" ", "\t", "\n", "\r", "\f", ""})
    @DisplayName("Ordinary whitespace is treated as blank and leaves the price untouched")
    void ordinaryWhitespaceIsIgnored(String voucherCode) {
        assertEquals(new BigDecimal("45.00"),
                service.calculateMonthlyPrice(SubscriptionTier.BASIC, 13, voucherCode));
    }
}
