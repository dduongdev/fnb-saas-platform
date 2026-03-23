package com.project.fnb.modules.global.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.fnb.modules.global.dto.PaymentConfigDto;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TenantControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TenantService tenantService;

    @InjectMocks
    private TenantController tenantController;

    private ObjectMapper objectMapper = new ObjectMapper();
    private static final String USER_ID = "owner-123";

    @BeforeEach
    void setUp() {
        Jwt mockJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject(USER_ID)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(tenantController)
                .setCustomArgumentResolvers(new TestJwtArgumentResolver(mockJwt))
                .build();
    }

    @Test
    void createTenant_ShouldReturnTenant() throws Exception {
        Tenant tenant = Tenant.builder()
                .id("t1")
                .name("Quán Test")
                .address("123 ABC")
                .ownerId(USER_ID)
                .build();

        when(tenantService.createTenant(any(), eq(USER_ID), isNull())).thenReturn(tenant);

        mockMvc.perform(multipart("/api/tenants")
                        .param("name", "Quán Test")
                        .param("address", "123 ABC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("t1"))
                .andExpect(jsonPath("$.data.name").value("Quán Test"))
                .andExpect(jsonPath("$.data.ownerId").value(USER_ID));

        verify(tenantService).createTenant(any(), eq(USER_ID), isNull());
    }

    @Test
    void updateTenant_ShouldReturnUpdatedTenant() throws Exception {
        Tenant updated = Tenant.builder()
                .id("t1")
                .name("Quán Updated")
                .address("456 DEF")
                .ownerId(USER_ID)
                .build();

        when(tenantService.updateTenant(eq("t1"), any(), isNull(), eq(USER_ID))).thenReturn(updated);

        mockMvc.perform(multipart("/api/tenants/t1")
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .param("name", "Quán Updated")
                        .param("address", "456 DEF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("Quán Updated"))
                .andExpect(jsonPath("$.data.address").value("456 DEF"));

        verify(tenantService).updateTenant(eq("t1"), any(), isNull(), eq(USER_ID));
    }

    @Test
    void getMyTenants_ShouldReturnList() throws Exception {
        Tenant t1 = Tenant.builder().id("t1").name("Quán 1").ownerId(USER_ID).build();
        Tenant t2 = Tenant.builder().id("t2").name("Quán 2").ownerId(USER_ID).build();

        when(tenantService.getMyTenants(USER_ID)).thenReturn(List.of(t1, t2));

        mockMvc.perform(get("/api/tenants/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("t1"))
                .andExpect(jsonPath("$.data[1].id").value("t2"));

        verify(tenantService).getMyTenants(USER_ID);
    }

    @Test
    void getTenantDetail_ShouldReturnTenant() throws Exception {
        Tenant tenant = Tenant.builder()
                .id("t1")
                .name("Quán Detail")
                .address("789 GHI")
                .build();

        when(tenantService.getTenantDetail("t1")).thenReturn(tenant);

        mockMvc.perform(get("/api/tenants/t1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("t1"))
                .andExpect(jsonPath("$.data.name").value("Quán Detail"));

        verify(tenantService).getTenantDetail("t1");
    }

    @Test
    void updateStatus_ShouldReturnSuccess() throws Exception {
        doNothing().when(tenantService).updateTenantStatus("t1", true, USER_ID);

        mockMvc.perform(patch("/api/tenants/t1/status")
                        .param("isActive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("Cập nhật trạng thái thành công"));

        verify(tenantService).updateTenantStatus("t1", true, USER_ID);
    }

    @Test
    void updatePaymentConfig_ShouldReturnSuccess() throws Exception {
        PaymentConfigDto config = new PaymentConfigDto();
        PaymentConfigDto.VNPayConfig vnpay = new PaymentConfigDto.VNPayConfig();
        vnpay.setTmnCode("TMN123");
        vnpay.setHashSecret("secret");
        vnpay.setEnabled(true);
        config.setVnpay(vnpay);

        doNothing().when(tenantService).updatePaymentConfig(eq("t1"), any(PaymentConfigDto.class), eq(USER_ID));

        mockMvc.perform(put("/api/tenants/t1/payment-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("Cập nhật cấu hình thanh toán thành công"));

        verify(tenantService).updatePaymentConfig(eq("t1"), any(PaymentConfigDto.class), eq(USER_ID));
    }
}
