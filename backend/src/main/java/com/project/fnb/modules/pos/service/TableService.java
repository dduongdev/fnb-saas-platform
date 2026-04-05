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
import java.util.Comparator;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service quản lý CRUD bàn ăn (Dining Tables).
 * 
 * <p><b>Scope:</b> Chỉ xử lý CRUD cơ bản cho bàn ăn. Các nghiệp vụ phức tạp như
 * merge/split/release tables đã được chuyển sang {@link SessionService}.</p>
 * 
 * <p><b>Business Rules:</b></p>
 * <ul>
 *   <li>Mỗi bàn có QR code duy nhất để khách quét và order</li>
 *   <li>Không được xóa bàn đang có khách (currentSession != null)</li>
 *   <li>Mọi thay đổi sẽ push realtime qua WebSocket topic {@code /topic/tenant/{tenantId}/tables}</li>
 * </ul>
 * 
 * <p><b>Use Cases:</b></p>
 * <ul>
 *   <li>Tạo bàn mới khi mở nhà hàng hoặc mở rộng phòng bàn</li>
 *   <li>Xóa bàn khi giảm sức chứa hoặc đóng cửa phòng bàn</li>
 *   <li>Lấy danh sách bàn để hiển thị floor map</li>
 * </ul>
 * 
 * @see SessionService
 * @see DiningTable
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
     * Tạo bàn ăn mới với QR code tự động.
     * 
     * <p><b>QR Code Generation:</b> Tạo QR code với format: {@code {frontendUrl}/menu/{tenantId}/{tableId}}
     * để khách có thể quét và truy cập menu đặt món.</p>
     * 
     * <p><b>WebSocket:</b> Sau khi tạo thành công, push event qua topic {@code /topic/tenant/{tenantId}/tables}
     * để cập nhật UI floor map realtime.</p>
     * 
     * @param name Tên bàn (VD: "Bàn 01", "VIP 1")
     * @return TableDto chứa thông tin bàn mới tạo kèm QR code URL
     * @throws AppException nếu có lỗi khi upload QR code lên storage
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
        String qrContent = String.format("%s/table/%s", frontendUrl, table.getId());
        MultipartFile qrFile = QrCodeUtils.generateQrCodeImage(qrContent, 300, 300);
        String qrUrl = storageService.uploadTenantImage(qrFile);
        
        table.setQrCodeUrl(qrUrl);
        tableRepository.save(table);

        notifyTableUpdate();
        return mapToDto(table);
    }

    /**
     * Lấy danh sách tất cả bàn ăn trong tenant hiện tại.
     * 
     * <p><b>Sort Order:</b> Danh sách được sắp xếp theo tên bàn (a-z).</p>
     * 
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Hiển thị floor map (sơ đồ bàn) trong POS UI</li>
     *   <li>Hiển thị dropdown chọn bàn khi mở session mới</li>
     * </ul>
     * 
     * <p><b>Performance:</b> Sử dụng findAllWithSession() để eager load currentSession,
     * tránh N+1 query khi mapToDto() access t.getCurrentSession(). Giải quyết N+1 query issue #9.</p>
     * 
     * @return Danh sách TableDto chứa id, name, status, sessionId, qrCodeUrl
     */
    public List<TableDto> getTables() {
        return tableRepository.findAllWithSession().stream()
                .sorted(Comparator.comparing(DiningTable::getName))
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Xóa bàn ăn (hard delete).
     * 
     * <p><b>Business Constraint:</b> Không được phép xóa bàn đang có khách
     * (currentSession != null). Phải thu tiền và đóng session trước khi xóa bàn.</p>
     * 
     * <p><b>WebSocket:</b> Sau khi xóa thành công, push event qua topic {@code /topic/tenant/{tenantId}/tables}
     * để cập nhật UI floor map.</p>
     * 
     * @param tableId ID của bàn cần xóa
     * @throws AppException 404 nếu bàn không tồn tại
     * @throws AppException 400 nếu bàn đang có khách (currentSession != null)
     */
    @Transactional
    public void deleteTable(String tableId) {
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