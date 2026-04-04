package com.project.fnb.modules.pos.controller;

import com.project.fnb.common.exception.GlobalExceptionHandler;
import com.project.fnb.modules.pos.dto.KdsSessionDto;
import com.project.fnb.modules.pos.service.KdsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class KdsRestControllerTest {

    private MockMvc mockMvc;

    @Mock
    private KdsService kdsService;

    @InjectMocks
    private KdsRestController kdsRestController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(kdsRestController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getSessions_ShouldReturnSessionList() throws Exception {
        KdsSessionDto session = KdsSessionDto.builder()
                .sessionId(1L)
                .tableNames("Bàn 1")
                .pendingItemCount(2)
                .build();

        when(kdsService.getAllActiveSessions()).thenReturn(List.of(session));

        mockMvc.perform(get("/api/pos/kds/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].sessionId").value(1L))
                .andExpect(jsonPath("$.data[0].tableNames").value("Bàn 1"));

        verify(kdsService).getAllActiveSessions();
    }

    @Test
    void getSessions_ShouldReturnInternalServerError_WhenServiceFails() throws Exception {
        when(kdsService.getAllActiveSessions()).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/api/pos/kds/sessions"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("boom")));
    }
}
