package com.project.fnb.modules.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.project.fnb.common.exception.GlobalExceptionHandler;
import com.project.fnb.modules.global.dto.PaymentConfigDto;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.payment.dto.CreatePaymentRequest;
import com.project.fnb.modules.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getPaymentMethods_CashOnly_WhenNoConfig() throws Exception {
        Tenant tenant = Tenant.builder().id("t1").name("Quán A").build();
        when(tenantRepository.findById("t1")).thenReturn(Optional.of(tenant));

        mockMvc.perform(get("/api/public/payment/methods/t1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("CASH"));
    }

    @Test
    void getPaymentMethods_WithVnpayEnabled() throws Exception {
        PaymentConfigDto config = new PaymentConfigDto();
        PaymentConfigDto.VNPayConfig vnpay = new PaymentConfigDto.VNPayConfig();
        vnpay.setEnabled(true);
        config.setVnpay(vnpay);

        Tenant tenant = Tenant.builder().id("t1").name("Quán B").paymentConfig(config).build();
        when(tenantRepository.findById("t1")).thenReturn(Optional.of(tenant));

        mockMvc.perform(get("/api/public/payment/methods/t1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("CASH"))
                .andExpect(jsonPath("$.data[1].code").value("VNPAY"));
    }

    @Test
    void getPaymentMethods_WithMomoEnabled() throws Exception {
        PaymentConfigDto config = new PaymentConfigDto();
        PaymentConfigDto.MomoConfig momo = new PaymentConfigDto.MomoConfig();
        momo.setEnabled(true);
        config.setMomo(momo);

        Tenant tenant = Tenant.builder().id("t1").name("Quán C").paymentConfig(config).build();
        when(tenantRepository.findById("t1")).thenReturn(Optional.of(tenant));

        mockMvc.perform(get("/api/public/payment/methods/t1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].code").value("MOMO"));
    }

    @Test
    void getPaymentMethods_TenantNotFound_ShouldReturn404() throws Exception {
        when(tenantRepository.findById("not-exist")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/public/payment/methods/not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void createPaymentUrl_ShouldReturnUrl() throws Exception {
        String expectedUrl = "https://pay.vnpay.vn/checkout?token=abc";
        when(paymentService.createPaymentUrl(eq(1L), eq("VNPAY"), anyString()))
                .thenReturn(expectedUrl);

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(1L)
                .paymentMethodCode("VNPAY")
                .build();

        mockMvc.perform(post("/api/public/payment/create-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(expectedUrl));

        verify(paymentService).createPaymentUrl(eq(1L), eq("VNPAY"), anyString());
    }

    @Test
    void vnpayIpn_Success_ShouldReturnCode00() throws Exception {
        when(paymentService.processPaymentCallback(any())).thenReturn("SUCCESS");

        mockMvc.perform(get("/api/public/payment/vnpay/ipn")
                        .param("vnp_TxnRef", "1_12345")
                        .param("vnp_ResponseCode", "00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"));
    }

    @Test
    void vnpayIpn_Error_ShouldReturnCode99() throws Exception {
        when(paymentService.processPaymentCallback(any()))
                .thenThrow(new RuntimeException("Payment failed"));

        mockMvc.perform(get("/api/public/payment/vnpay/ipn")
                        .param("vnp_TxnRef", "1_12345")
                        .param("vnp_ResponseCode", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("99"))
                .andExpect(jsonPath("$.Message").value("Unknown error"));
    }
}
