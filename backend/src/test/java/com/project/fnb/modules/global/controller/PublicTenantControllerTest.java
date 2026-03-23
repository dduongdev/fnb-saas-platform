package com.project.fnb.modules.global.controller;

import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicTenantControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private PublicTenantController publicTenantController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(publicTenantController).build();
    }

    @Test
    void getPublicTenants_ShouldReturnPage() throws Exception {
        Tenant tenant = Tenant.builder()
                .id("t1")
                .name("KFC Vietnam")
                .address("123 Nguyen Hue")
                .logoUrl("http://logo.png")
                .build();

        Page<Tenant> page = new PageImpl<>(java.util.List.of(tenant), org.springframework.data.domain.PageRequest.of(0, 10), 1);
        when(tenantRepository.findByIsActiveTrue(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/public/tenants")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value("t1"))
                .andExpect(jsonPath("$.data.content[0].name").value("KFC Vietnam"))
                .andExpect(jsonPath("$.data.content[0].address").value("123 Nguyen Hue"));

        verify(tenantRepository).findByIsActiveTrue(any(Pageable.class));
    }

    @Test
    void getPublicTenants_DefaultParams_ShouldReturnPage() throws Exception {
        Page<Tenant> emptyPage = new PageImpl<>(java.util.List.of(), org.springframework.data.domain.PageRequest.of(0, 10), 0);
        when(tenantRepository.findByIsActiveTrue(any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/api/public/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isEmpty());
    }
}
