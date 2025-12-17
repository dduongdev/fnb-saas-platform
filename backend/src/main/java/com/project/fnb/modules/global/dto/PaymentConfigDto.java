package com.project.fnb.modules.global.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfigDto {

    private VNPayConfig vnpay;
    private MomoConfig momo;
    private PayPalConfig paypal;

    // --- INNER CLASSES ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VNPayConfig {
        // TmnCode: Mã định danh website do VNPay cấp
        private String tmnCode;
        
        // HashSecret: Chuỗi bí mật để tạo checksum
        private String hashSecret;
        
        private Boolean enabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MomoConfig {
        private String partnerCode;
        private String accessKey;
        private String secretKey;
        private Boolean enabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayPalConfig {
        private String clientId;
        private String clientSecret;
        private String mode; // sandbox / live
        private Boolean enabled;
    }
}