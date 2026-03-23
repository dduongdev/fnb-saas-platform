package com.project.fnb.modules.pos.controller;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.common.exception.GlobalExceptionHandler;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.pos.dto.TableDto;
import com.project.fnb.modules.pos.entity.DiningTable;
import com.project.fnb.modules.pos.service.TableService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class TableControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TableService tableService;

    @InjectMocks
    private TableController tableController;

    private static final String TENANT_ID = "tenant-1";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(tableController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getTables_ShouldReturnList() throws Exception {
        TableDto table1 = TableDto.builder().build();
        table1.setId("t1");
        table1.setName("Table 1");
        table1.setStatus(DiningTable.Status.AVAILABLE);

        TableDto table2 = TableDto.builder().build();
        table2.setId("t2");
        table2.setName("Table 2");
        table2.setStatus(DiningTable.Status.OCCUPIED);

        List<TableDto> tables = Arrays.asList(table1, table2);

        when(tableService.getTables()).thenReturn(tables);

        mockMvc.perform(get("/api/pos/tables")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value("t1"))
                .andExpect(jsonPath("$.data[1].id").value("t2"));

        verify(tableService).getTables();
    }

    @Test
    void createTable_ShouldReturnTable() throws Exception {
        TableDto table = TableDto.builder().build();
        table.setId("t1");
        table.setName("New Table");
        table.setStatus(DiningTable.Status.AVAILABLE);

        when(tableService.createTable("New Table")).thenReturn(table);

        mockMvc.perform(post("/api/pos/tables")
                .param("name", "New Table")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("New Table"));

        verify(tableService).createTable("New Table");
    }

    @Test
    void deleteTable_ShouldReturnSuccess() throws Exception {
        doNothing().when(tableService).deleteTable("t1");

        mockMvc.perform(delete("/api/pos/tables/t1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(tableService).deleteTable("t1");
    }

    @Test
    void deleteTable_WhenOccupied_ShouldThrowAppException() throws Exception {
        doThrow(new AppException(400, "Bàn đang được sử dụng")).when(tableService).deleteTable("t1");

        mockMvc.perform(delete("/api/pos/tables/t1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Bàn đang được sử dụng"));

        verify(tableService).deleteTable("t1");
    }
}
