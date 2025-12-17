package com.project.fnb.modules.payment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentMethodResponse {
    private String code; // VNPAY, MOMO, CASH
    private String name; // "VNPay QR", "Ví Momo", "Tiền mặt"
    private String iconUrl; // URL logo của cổng thanh toán (để FE hiển thị cho đẹp)
}