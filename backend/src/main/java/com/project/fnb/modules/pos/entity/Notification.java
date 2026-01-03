package com.project.fnb.modules.pos.entity;

import com.project.fnb.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * Entity lưu trữ thông báo cho chủ quán/nhân viên.
 * 
 * <p><b>Mục đích:</b> Lưu trữ tất cả thông báo liên quan đến hoạt động của khách hàng
 * để chủ quán có thể theo dõi và phục vụ khách hàng nhanh chóng.</p>
 * 
 * <p><b>Notification Types:</b></p>
 * <ul>
 *   <li>NEW_SESSION - Khách mở phiên mới</li>
 *   <li>NEW_ORDER - Khách gọi món mới</li>
 *   <li>ADD_ITEM - Khách thêm món</li>
 *   <li>REMOVE_ITEM - Khách xóa món</li>
 *   <li>UPDATE_ITEM - Khách đổi số lượng</li>
 *   <li>PAYMENT_REQUESTED - Khách yêu cầu thanh toán</li>
 *   <li>PAYMENT_SUCCESS - Thanh toán thành công</li>
 *   <li>SESSION_CONFIRMED - Nhân viên xác nhận đơn</li>
 *   <li>SESSION_REJECTED - Nhân viên từ chối đơn</li>
 * </ul>
 * 
 * <p><b>Priority Levels:</b></p>
 * <ul>
 *   <li>HIGH - Cần xử lý ngay (yêu cầu thanh toán, đơn mới)</li>
 *   <li>MEDIUM - Quan trọng nhưng không khẩn cấp (thêm/xóa món)</li>
 *   <li>LOW - Thông tin (xác nhận, hoàn thành)</li>
 * </ul>
 * 
 * @author FNB Team
 * @version 1.0
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE notifications SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Loại thông báo (NEW_ORDER, ADD_ITEM, REMOVE_ITEM, PAYMENT_REQUESTED, etc.)
     */
    @Column(nullable = false, length = 50)
    private String type;

    /**
     * Tiêu đề ngắn gọn của thông báo
     */
    @Column(nullable = false, length = 200)
    private String title;

    /**
     * Nội dung chi tiết thông báo
     */
    @Column(length = 500)
    private String content;

    /**
     * Mức độ ưu tiên: HIGH, MEDIUM, LOW
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    /**
     * Session liên quan (nếu có)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ServingSession session;

    /**
     * Table liên quan (nếu có)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private DiningTable table;

    /**
     * Tên bàn (denormalized để hiển thị nhanh)
     */
    @Column(name = "table_name", length = 50)
    private String tableName;

    /**
     * Đã đọc chưa
     */
    @Column(name = "is_read")
    @Builder.Default
    private Boolean isRead = false;

    /**
     * Thời gian đọc
     */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /**
     * Enum mức độ ưu tiên
     */
    public enum Priority {
        /** Cần xử lý ngay */
        HIGH,
        /** Quan trọng nhưng không khẩn cấp */
        MEDIUM,
        /** Thông tin */
        LOW
    }
}
