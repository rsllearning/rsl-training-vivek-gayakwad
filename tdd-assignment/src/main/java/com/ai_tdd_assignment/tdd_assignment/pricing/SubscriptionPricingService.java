package com.ai_tdd_assignment.tdd_assignment.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class SubscriptionPricingService {

    private static final String SAVE20 = "SAVE20";
    private static final String HALFPRICE = "HALFPRICE";

    private static final PriceAdjustment NONE = new PriceAdjustment.None();
    private static final PriceAdjustment TEN_PERCENT_OFF = new PriceAdjustment.Multiplier(new BigDecimal("0.90"));
    private static final PriceAdjustment TWENTY_FIVE_PERCENT_OFF = new PriceAdjustment.Multiplier(new BigDecimal("0.75"));
    private static final PriceAdjustment HALF_PRICE = new PriceAdjustment.Multiplier(new BigDecimal("0.50"));
    private static final PriceAdjustment SAVE20_DEDUCTION = new PriceAdjustment.FlatDeduction(new BigDecimal("20.00"));

    /** The two shapes a price change can take. Sealed, so {@link #apply} must handle every one. */
    private sealed interface PriceAdjustment {
        record None() implements PriceAdjustment {}
        record Multiplier(BigDecimal factor) implements PriceAdjustment {}
        record FlatDeduction(BigDecimal amount) implements PriceAdjustment {}
    }

    public BigDecimal calculateMonthlyPrice(SubscriptionTier tier, int activeMonths, String voucherCode) {
        Objects.requireNonNull(tier, "tier must not be null");
        if (activeMonths < 0) {
            throw new IllegalArgumentException("activeMonths must not be negative but was " + activeMonths);
        }

        // Order matters: the voucher applies to the longevity-discounted rate, not the base rate.
        BigDecimal discounted = apply(tier.getBaseMonthlyRate(), longevityDiscountFor(activeMonths));
        BigDecimal vouchered = apply(discounted, voucherFor(voucherCode));
        return vouchered.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal apply(BigDecimal rate, PriceAdjustment adjustment) {
        return switch (adjustment) {
            case PriceAdjustment.None() -> rate;
            case PriceAdjustment.Multiplier(BigDecimal factor) -> rate.multiply(factor);
            case PriceAdjustment.FlatDeduction(BigDecimal amount) -> rate.subtract(amount);
        };
    }

    private static PriceAdjustment longevityDiscountFor(int activeMonths) {
        if (activeMonths > 36) {
            return TWENTY_FIVE_PERCENT_OFF;
        }
        if (activeMonths > 12) {
            return TEN_PERCENT_OFF;
        }
        return NONE;
    }

    private static PriceAdjustment voucherFor(String voucherCode) {
        if (voucherCode == null || voucherCode.isBlank()) {
            return NONE;
        }
        return switch (voucherCode) {
            case SAVE20 -> SAVE20_DEDUCTION;
            case HALFPRICE -> HALF_PRICE;
            default -> throw new InvalidVoucherException(voucherCode);
        };
    }
}
