package com.project.fnb.modules.payment.enums;

public enum PaymentProvider {
    VNPAY,
    MOMO,
    ZALOPAY,
    PAYPAL;

    // Helper an toàn để convert từ String
    public static PaymentProvider from(String code) {
        try {
            return PaymentProvider.valueOf(code.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}