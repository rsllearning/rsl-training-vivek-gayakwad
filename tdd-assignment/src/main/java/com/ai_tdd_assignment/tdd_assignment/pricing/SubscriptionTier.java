package com.ai_tdd_assignment.tdd_assignment.pricing;

import java.math.BigDecimal;

public enum SubscriptionTier {

    BASIC("50.00"),
    PRO("150.00"),
    ENTERPRISE("500.00");

    private final BigDecimal baseMonthlyRate;

    SubscriptionTier(String baseMonthlyRate) {
        this.baseMonthlyRate = new BigDecimal(baseMonthlyRate);
    }

    public BigDecimal getBaseMonthlyRate() {
        return baseMonthlyRate;
    }
}
