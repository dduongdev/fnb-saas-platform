package com.project.fnb.modules.pos.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.common.utils.QrCodeUtils;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.infrastructure.storage.StorageService;
import com.project.fnb.modules.pos.dto.TableDto;
import com.project.fnb.modules.pos.entity.DiningTable;
import com.project.fnb.modules.pos.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * TableService - Quản lý CRUD bàn ăn.
 * 
 * <p>Các nghiệp vụ merge/split/release đã được chuyển sang SessionService.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TableService {

    private final TableRepository tableRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final StorageService storageService;

    @Value("${app.frontend.url}") 
    private String frontendUrl;

    /**
     * Tạo bàn mới với QR code.
     */
    @Transactional
    public TableDto createTable(String name) {
        DiningTable table = DiningTable.builder()
                .name(name)
                .status(DiningTable.Status.AVAILABLE)
                .build();
        
        table = tableRepository.save(table);

        // Generate QR Code
        String tenantId = TenantContext.getTenantId();
        String qrContent = String.format("%s/menu/%s/%d", frontendUrl, tenantId, table.getId());
        MultipartFile qrFile = QrCodeUtils.generateQrCodeImage(qrContent, 300, 300);
        String qrUrl = storageService.uploadTenantImage(qrFile);
        
        table.setQrCodeUrl(qrUrl);
        tableRepository.save(table);

        notifyTableUpdate();
        return mapToDto(table);
    }

    /**
     * Lấy danh sách bàn.
     */
    public List<TableDto> getTables() {
        return tableRepository.findAll(Sort.by("name")).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Xóa bàn (soft delete).
     */
    @Transactional
    public void deleteTable(Integer tableId) {
        DiningTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new AppException(404, "Bàn không tồn tại"));
        
        if (table.getCurrentSession() != null) {
            throw new AppException(400, "Không thể xóa bàn đang có khách.");
        }
        
        tableRepository.delete(table);
        notifyTableUpdate();
    }

    private TableDto mapToDto(DiningTable t) {
        return TableDto.builder()
                .id(t.getId())
                .name(t.getName())
                .status(t.getStatus())
                .sessionId(t.getCurrentSession() != null ? t.getCurrentSession().getId() : null)
                .qrCodeUrl(t.getQrCodeUrl())
                .build();
    }

    public void notifyTableUpdate() {
        String tenantId = TenantContext.getTenantId();
        try {
            String topic = "/topic/tenant/" + tenantId + "/tables";
            List<TableDto> tables = getTables();
            messagingTemplate.convertAndSend(topic, tables);
        } catch (Exception e) {
            log.error("Socket error: " + e.getMessage());
        }
    }
}