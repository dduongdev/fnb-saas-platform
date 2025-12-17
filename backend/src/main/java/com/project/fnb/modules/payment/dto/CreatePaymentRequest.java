package com.project.fnb.modules.payment.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
@Builder
public class CreatePaymentRequest {
    private Long orderId;
    private String paymentMethodCode;
}
