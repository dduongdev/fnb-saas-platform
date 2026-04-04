package com.project.fnb.modules.pos.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO đại diện cho một ServingSession trong Kitchen Display System (KDS).
 * 
 * <p><b>Mục đích:</b> Hiển thị một cột (phiên order) trên màn hình KDS, 
 * bao gồm danh sách các bàn và danh sách các món ăn cần chế biến.</p>
 * 
 * <p><b>Sắp xếp logic:</b></p>
 * <ul>
 *   <li>Sessions sắp xếp theo createdAt (mới nhất trước)</li>
 *   <li>Items trong session sắp xếp: PENDING items trước, SERVED items cuối</li>
 * </ul>
 * 
 * @author FNB Team
 * @version 1.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KdsSessionDto {
    
    /**
     * ID của ServingSession.
     */
    private Long sessionId;
    
    /**
     * Danh sách các bàn ăn dưới dạng chuỗi, ngăn cách bằng dấu phẩy.
     * Ví dụ: "Bàn 1, Bàn 2, Bàn 3"
     */
    private String tableNames;
    
    /**
     * Danh sách OrderItems trong session này (sắp xếp: PENDING trước, SERVED cuối).
     */
    private List<KdsOrderItemDto> items;
    
    /**
     * Tổng thời gian chờ tính bằng phút (dựa trên item cũ nhất trong session).
     * Tính toán: (now - minCreatedAtOfItems) / 60000
     */
    private Long totalMinutesWaited;
    
    /**
     * Số lượng món còn đang chờ/nấu (status = PENDING).
     */
    private Integer pendingItemCount;
    
    /**
     * Thời điểm session được tạo.
     */
    private LocalDateTime sessionCreatedAt;
}
