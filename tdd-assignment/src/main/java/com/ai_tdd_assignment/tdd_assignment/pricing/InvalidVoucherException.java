package com.ai_tdd_assignment.tdd_assignment.pricing;

public class InvalidVoucherException extends RuntimeException {

    public InvalidVoucherException(String voucherCode) {
        super("Unknown voucher code: '" + voucherCode + "'");
    }
}
