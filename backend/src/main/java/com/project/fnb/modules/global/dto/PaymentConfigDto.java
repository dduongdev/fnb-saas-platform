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
    private String vnpTmnCode;    // Mã định danh Terminal
    private String vnpHashSecret; // Secret Key (Cần bảo mật)
}