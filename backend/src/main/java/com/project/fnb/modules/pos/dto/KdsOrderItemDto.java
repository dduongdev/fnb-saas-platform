package com.project.fnb.modules.pos.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO đại diện cho một OrderItem trong Kitchen Display System (KDS).
 * 
 * <p><b>Mục đích:</b> Hiển thị danh sách các món ăn cần chế biến trên màn hình KDS.
 * Chỉ chứa thông tin cần thiết cho bếp.</p>
 * 
 * <p><b>Trạng thái:</b></p>
 * <ul>
 *   <li>PENDING: Món đang chờ hoặc đang nấu</li>
 *   <li>SERVED: Món đã hoàn thành và được phục vụ</li>
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
public class KdsOrderItemDto {
    
    /**
     * ID của OrderItem.
     */
    private Long itemId;
    
    /**
     * Tên sản phẩm.
     */
    private String productName;
    
    /**
     * Số lượng.
     */
    private Integer quantity;
    
    /**
     * Ghi chú của khách/nhân viên (ví dụ: "không tiêu", "thêm riêu", v.v...).
     */
    private String notes;
    
    /**
     * Trạng thái của món: PENDING (đang chờ/nấu), SERVED (đã xong).
     */
    private String status;
    
    /**
     * Thời điểm tạo món (từ OrderItem.createdAt).
     */
    private LocalDateTime createdAt;
    
    /**
     * Tên bàn gốc (lấy từ originalTable nếu có).
     */
    private String originalTableName;
}
