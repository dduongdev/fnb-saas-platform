package com.project.fnb.modules.pos.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.modules.pos.dto.TableDto;
import com.project.fnb.modules.pos.entity.DiningTable;
import com.project.fnb.modules.pos.entity.ServingSession;
import com.project.fnb.modules.pos.repository.TableRepository;
import com.project.fnb.infrastructure.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TableServiceTest {

    @Mock
    private TableRepository tableRepository;

    @Mock
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Mock
    private com.project.fnb.infrastructure.storage.StorageService storageService;

    @InjectMocks
    private TableService tableService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("testTenant");
    }
    
    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getTables_ShouldReturnList() {
        DiningTable table = new DiningTable();
        table.setId("T1");
        table.setName("Table 1");
        table.setStatus(DiningTable.Status.AVAILABLE);

        when(tableRepository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(table));

        List<TableDto> result = tableService.getTables();

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("T1", result.get(0).getId());
    }

    @Test
    void createTable_ShouldSaveAndNotify() {
        DiningTable table = new DiningTable();
        table.setId("T2");
        table.setName("New Table");
        table.setStatus(DiningTable.Status.AVAILABLE);

        when(tableRepository.save(any(DiningTable.class))).thenAnswer(i -> {
            DiningTable t = i.getArgument(0);
            if (t.getId() == null) t.setId("T2");
            return t;
        });

        when(storageService.uploadTenantImage(any(MultipartFile.class))).thenReturn("http://qr.img");

        TableDto result = tableService.createTable("New Table");

        assertNotNull(result);
        assertEquals("New Table", result.getName());
        assertEquals("http://qr.img", result.getQrCodeUrl());
        verify(tableRepository, times(2)).save(any(DiningTable.class));
        verify(messagingTemplate).convertAndSend(anyString(), anyList());
    }

    @Test
    void deleteTable_ShouldDeleteAndNotify() {
        DiningTable table = new DiningTable();
        table.setId("T3");
        table.setName("Table 3");
        
        when(tableRepository.findById("T3")).thenReturn(Optional.of(table));

        tableService.deleteTable("T3");

        verify(tableRepository).delete(table);
        verify(messagingTemplate).convertAndSend(anyString(), anyList());
    }

    @Test
    void deleteTable_WithCurrentSession_ShouldThrowException() {
        DiningTable table = new DiningTable();
        table.setId("T4");
        table.setCurrentSession(new ServingSession());
        
        when(tableRepository.findById("T4")).thenReturn(Optional.of(table));

        AppException ex = assertThrows(AppException.class, () -> tableService.deleteTable("T4"));
        assertEquals(400, ex.getErrorCode());
    }

    @Test
    void deleteTable_NotFound_ShouldThrowException() {
        when(tableRepository.findById("T5")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> tableService.deleteTable("T5"));
        assertEquals(404, ex.getErrorCode());
    }
}