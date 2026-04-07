package com.project.fnb.modules.pos.service;

import com.project.fnb.modules.pos.dto.KdsOrderItemDto;
import com.project.fnb.modules.pos.dto.KdsSessionDto;
import com.project.fnb.modules.pos.entity.OrderItem;
import com.project.fnb.modules.pos.entity.ServingSession;
import com.project.fnb.modules.pos.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service quản lý Kitchen Display System (KDS).
 * 
 * <p><b>Mục đích:</b> Cung cấp dữ liệu transformations và queries cho KDS display.
 * Bao gồm logic sắp xếp, lọc, và tính toán wait time.</p>
 * 
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Lấy tất cả active sessions với pending items</li>
 *   <li>Transform Session entities → KdsSessionDto (for WebSocket broadcast)</li>
 *   <li>Sắp xếp sessions theo thời gian tạo (mới nhất trước)</li>
 *   <li>Sắp xếp items: PENDING trước, SERVED cuối</li>
 *   <li>Tính toán total wait time per session</li>
 * </ul>
 * 
 * @author FNB Team
 * @version 1.0
 * @see KdsSessionDto
 * @see KdsOrderItemDto
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KdsService {
    
    private final SessionRepository sessionRepository;
    
    /**
     * Lấy tất cả active sessions với các OrderItems pending/served để hiển thị trên KDS.
     * 
     * <p><b>Logic:</b></p>
     * <ul>
     *   <li>Query tất cả ACTIVE sessions</li>
     *   <li>Transform thành KdsSessionDto (bao gồm cả PENDING và SERVED items)</li>
     *   <li>Sắp xếp sessions theo createdAt DESC (mới nhất trước)</li>
     * </ul>
     * 
     * @return Danh sách KdsSessionDto sắp xếp theo thời gian tạo
     */
    @Transactional(readOnly = true)
    public List<KdsSessionDto> getAllActiveSessions() {
        // Fix N+1 query: Use findAllActiveWithOrdersAndItemsAndProducts() with complete JOIN FETCH
        // Before: 1 query (tables only) + N queries (orders) + M queries (items) + K queries (products)
        // After: 1 query with JOIN FETCH chain: orders → items → products = 1 query
        List<ServingSession> sessions = sessionRepository.findAllActiveWithOrdersAndItemsAndProducts();
        
        return sessions.stream()
                .map(this::transformToKdsSessionDto)
                .sorted(Comparator.comparing(KdsSessionDto::getSessionCreatedAt).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * Lấy danh sách items pending cho một session cụ thể.
     * 
     * <p><b>Use Case:</b> Khi có sự kiện item mới được thêm, cần lấy các pending items
     * để update trên KDS screen.</p>
     * 
     * @param sessionId ID của session
     * @return Danh sách KdsOrderItemDto với status = PENDING
     */
    @Transactional(readOnly = true)
    public List<KdsOrderItemDto> getPendingItemsForSession(Long sessionId) {
        return sessionRepository.findActiveByIdWithDetails(sessionId)
                .stream()
                .flatMap(s -> s.getOrders().stream()
                        .flatMap(o -> o.getItems().stream()
                                .filter(i -> i.getStatus() == OrderItem.ItemStatus.PENDING)
                                .map(this::transformToKdsOrderItemDto)))
                .collect(Collectors.toList());
    }
    
    /**
     * Transform ServingSession entity → KdsSessionDto.
     * 
     * <p><b>Logic:</b></p>
     * <ul>
     *   <li>Lấy danh sách tên bàn từ session.tables</li>
     *   <li>Lấy tất cả items từ tất cả orders trong session</li>
     *   <li>Sắp xếp items: PENDING trước (theo createdAt ASC), SERVED cuối</li>
     *   <li>Tính toán pendingItemCount và totalMinutesWaited</li>
     * </ul>
     * 
     * @param session ServingSession entity
     * @return KdsSessionDto
     */
    private KdsSessionDto transformToKdsSessionDto(ServingSession session) {
        // Lấy danh sách tên bàn
        String tableNames = session.getTables().stream()
                .map(t -> t.getName() != null ? t.getName() : "Bàn " + t.getId())
                .collect(Collectors.joining(", "));
        
        // Lấy tất cả items từ tất cả orders trong session
        List<KdsOrderItemDto> allItems = session.getOrders().stream()
                .flatMap(o -> o.getItems().stream())
                .map(this::transformToKdsOrderItemDto)
                .collect(Collectors.toList());
        
        // Sắp xếp: PENDING items trước (theo createdAt ASC - item cũ nhất trước), SERVED items cuối
        List<KdsOrderItemDto> sortedItems = allItems.stream()
                .sorted(Comparator.comparing((KdsOrderItemDto item) -> "PENDING".equals(item.getStatus()) ? 0 : 1)
                        .thenComparing(KdsOrderItemDto::getCreatedAt))
                .collect(Collectors.toList());
        
        // Tính số lượng items PENDING
        int pendingCount = (int) sortedItems.stream()
                .filter(item -> "PENDING".equals(item.getStatus()))
                .count();
        
        // Tính totalMinutesWaited dựa trên item cũ nhất (createdAt nhỏ nhất)
        Long totalMinutesWaited = sortedItems.stream()
                .map(KdsOrderItemDto::getCreatedAt)
                .min(Comparator.naturalOrder())
                .map(oldest -> ChronoUnit.MINUTES.between(oldest, LocalDateTime.now()))
                .orElse(0L);
        
        return KdsSessionDto.builder()
                .sessionId(session.getId())
                .tableNames(tableNames)
                .items(sortedItems)
                .pendingItemCount(pendingCount)
                .totalMinutesWaited(totalMinutesWaited)
                .sessionCreatedAt(session.getCreatedAt())
                .build();
    }
    
    /**
     * Transform OrderItem entity → KdsOrderItemDto.
     * 
     * <p><b>Thông tin cần thiết cho KDS:</b></p>
     * <ul>
     *   <li>ID, Product Name, Quantity, Notes, Status</li>
     *   <li>CreatedAt (để tính wait time)</li>
     *   <li>OriginalTableName (nếu có)</li>
     * </ul>
     * 
     * @param item OrderItem entity
     * @return KdsOrderItemDto
     */
    private KdsOrderItemDto transformToKdsOrderItemDto(OrderItem item) {
        String originalTableName = item.getOriginalTable() != null 
                ? (item.getOriginalTable().getName() != null 
                    ? item.getOriginalTable().getName() 
                    : "Bàn " + item.getOriginalTable().getId())
                : null;
        
        String status = item.getStatus() != null ? item.getStatus().toString() : "PENDING";
        
        return KdsOrderItemDto.builder()
                .itemId(item.getId())
                .productName(item.getProduct() != null ? item.getProduct().getName() : "Unknown Product")
                .quantity(item.getQuantity())
                .notes(item.getNote())
                .status(status)
                .createdAt(item.getCreatedAt())
                .originalTableName(originalTableName)
                .build();
    }
}
