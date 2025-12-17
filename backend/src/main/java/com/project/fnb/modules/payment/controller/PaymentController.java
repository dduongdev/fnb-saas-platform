package com.project.fnb.modules.payment.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.common.exception.AppException;
import com.project.fnb.modules.global.dto.PaymentConfigDto;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.payment.dto.CreatePaymentRequest;
import com.project.fnb.modules.payment.dto.PaymentMethodResponse;
import com.project.fnb.modules.payment.service.PaymentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/public/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final TenantRepository tenantRepository;
    private final PaymentService paymentService;

    @GetMapping("/methods/{tenantId}")
    public ApiResponse<List<PaymentMethodResponse>> getPaymentMethods(@PathVariable String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(404, "Quán không tồn tại"));

        PaymentConfigDto config = tenant.getPaymentConfig();
        List<PaymentMethodResponse> methods = new ArrayList<>();

        // 1. Luôn có Tiền mặt (hoặc tùy config quán)
        methods.add(PaymentMethodResponse.builder()
                .code("CASH").name("Tiền mặt").iconUrl("https://.../cash.png").build());

        if (config != null) {
            // 2. Check VNPay
            if (config.getVnpay() != null && Boolean.TRUE.equals(config.getVnpay().getEnabled())) {
                methods.add(PaymentMethodResponse.builder()
                        .code("VNPAY").name("VNPay QR").iconUrl("https://.../vnpay.png").build());
            }

            // 3. Check Momo
            if (config.getMomo() != null && Boolean.TRUE.equals(config.getMomo().getEnabled())) {
                methods.add(PaymentMethodResponse.builder()
                        .code("MOMO").name("Ví Momo").iconUrl("https://.../momo.png").build());
            }
        }

        return ApiResponse.success(methods);
    }

    @PostMapping("/create-url")
    public ApiResponse<String> createPaymentUrl(
            @RequestBody CreatePaymentRequest request // chứa orderId, paymentMethodCode
    ) {
        // request.getPaymentMethodCode() sẽ là "VNPAY" hoặc "MOMO"
        String url = paymentService.createPaymentUrl(
                request.getOrderId(), 
                request.getPaymentMethodCode(), 
                "127.0.0.1" // IP Address
        );
        return ApiResponse.success(url);
    }
}