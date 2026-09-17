package com.ai_tdd_assignment.tdd_assignment.pricing;

public class InvalidVoucherException extends RuntimeException {

    private static final int MAX_ECHOED_LENGTH = 64;

    public InvalidVoucherException(String voucherCode) {
        super("Unknown voucher code: '" + forLogging(voucherCode) + "'");
    }

    // The code comes straight from the caller and lands in a log line, so cap its length and
    // escape line breaks - a raw newline here would let a caller forge log records.
    private static String forLogging(String voucherCode) {
        String capped = voucherCode.length() <= MAX_ECHOED_LENGTH
                ? voucherCode
                : voucherCode.substring(0, MAX_ECHOED_LENGTH) + "...";
        return capped.replace("\r", "\\r").replace("\n", "\\n");
    }
}
