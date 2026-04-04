package com.project.fnb.modules.pos.controller;

import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.pos.entity.PosActionAudit;
import com.project.fnb.modules.pos.service.PosActionAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PosActionAuditControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PosActionAuditService auditService;

    @InjectMocks
    private PosActionAuditController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        TenantContext.setTenantId("tenant-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getAuditEvents_ShouldReturnPagedResult_WithFilters() throws Exception {
        PosActionAudit item = PosActionAudit.builder().id("a-1").action("PAY").tenantId("tenant-1").build();
        Page<PosActionAudit> pageData = new PageImpl<>(List.of(item), PageRequest.of(1, 5), 1);

        when(auditService.queryAudit("tenant-1", "PAY", "u-1", "k-1", "SESSION", 1, 5))
                .thenReturn(pageData);

        mockMvc.perform(get("/api/pos/audit")
                        .param("page", "1")
                        .param("size", "5")
                        .param("action", "PAY")
                        .param("userId", "u-1")
                        .param("accessKeyId", "k-1")
                        .param("targetType", "SESSION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value("a-1"));

        verify(auditService).queryAudit("tenant-1", "PAY", "u-1", "k-1", "SESSION", 1, 5);
    }

    @Test
    void getAuditEvents_ShouldUseDefaultPagination() throws Exception {
        when(auditService.queryAudit("tenant-1", null, null, null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/pos/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(auditService).queryAudit("tenant-1", null, null, null, null, 0, 20);
    }
}
