package com.project.fnb.modules.global.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.fnb.modules.global.dto.AccessKeyDto;
import com.project.fnb.modules.global.dto.CreateAccessKeyRequest;
import com.project.fnb.modules.global.entity.AccessRole;
import com.project.fnb.modules.global.service.AccessKeyService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class AccessKeyControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AccessKeyService accessKeyService;

    @InjectMocks
    private AccessKeyController accessKeyController;

    private ObjectMapper objectMapper = new ObjectMapper();
    private static final String USER_ID = "owner-123";

    @BeforeEach
    public void setup() {
        Jwt mockJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject(USER_ID)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(accessKeyController)
                .setCustomArgumentResolvers(new TestJwtArgumentResolver(mockJwt))
                .build();
    }

    @Test
    void createKey_Success() throws Exception {
        CreateAccessKeyRequest request = new CreateAccessKeyRequest();
        request.setName("New Key");
        request.setRole(AccessRole.WAITER);

        AccessKeyDto responseDto = AccessKeyDto.builder()
                .id("key-1")
                .name("New Key")
                .keyString("random-str")
                .role(AccessRole.WAITER)
                .isActive(true)
                .build();

        when(accessKeyService.createKey(eq("tenant-1"), eq(USER_ID), any(CreateAccessKeyRequest.class)))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/tenants/tenant-1/access-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("key-1"))
                .andExpect(jsonPath("$.data.name").value("New Key"));
    }

    @Test
    void getKeys_Success() throws Exception {
        AccessKeyDto responseDto = AccessKeyDto.builder()
                .id("key-1")
                .name("New Key")
                .keyString("random-str")
                .role(AccessRole.WAITER)
                .isActive(true)
                .build();

        when(accessKeyService.getKeysByTenant(eq("tenant-1"), eq(USER_ID)))
                .thenReturn(List.of(responseDto));

        mockMvc.perform(get("/api/tenants/tenant-1/access-keys")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("key-1"));
    }

    @Test
    void revokeKey_Success() throws Exception {
        doNothing().when(accessKeyService).revokeKey(eq("tenant-1"), eq("key-1"), eq(USER_ID));

        mockMvc.perform(delete("/api/tenants/tenant-1/access-keys/key-1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
