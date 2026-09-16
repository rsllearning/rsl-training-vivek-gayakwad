package com.ai_tdd_assignment.tdd_assignment.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class SubscriptionPricingService {

    private static final String SAVE20 = "SAVE20";
    private static final String HALFPRICE = "HALFPRICE";

    private static final BigDecimal TEN_PERCENT_OFF = new BigDecimal("0.90");
    private static final BigDecimal TWENTY_FIVE_PERCENT_OFF = new BigDecimal("0.75");
    private static final BigDecimal HALF = new BigDecimal("0.50");
    private static final BigDecimal TWENTY = new BigDecimal("20.00");

    public BigDecimal calculateMonthlyPrice(SubscriptionTier tier, int activeMonths, String voucherCode) {
        Objects.requireNonNull(tier, "tier must not be null");
        if (activeMonths < 0) {
            throw new IllegalArgumentException("activeMonths must not be negative but was " + activeMonths);
        }

        BigDecimal rate = applyLongevityDiscount(tier.getBaseMonthlyRate(), activeMonths);
        return applyVoucher(rate, voucherCode).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal applyLongevityDiscount(BigDecimal baseRate, int activeMonths) {
        if (activeMonths > 36) {
            return baseRate.multiply(TWENTY_FIVE_PERCENT_OFF);
        }
        if (activeMonths > 12) {
            return baseRate.multiply(TEN_PERCENT_OFF);
        }
        return baseRate;
    }

    private BigDecimal applyVoucher(BigDecimal rate, String voucherCode) {
        if (voucherCode == null || voucherCode.isBlank()) {
            return rate;
        }
        return switch (voucherCode) {
            case SAVE20 -> rate.subtract(TWENTY);
            case HALFPRICE -> rate.multiply(HALF);
            default -> throw new InvalidVoucherException(voucherCode);
        };
    }
}
