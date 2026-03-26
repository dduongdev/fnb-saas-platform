package com.project.fnb.modules.pos.entity;

import com.project.fnb.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entity đại diện cho một phiên phục vụ khách hàng.
 * 
 * <p>
 * <strong>Session-based Model (Refactored):</strong>
 * </p>
 * <p>
 * Session là khái niệm trung tâm, thay thế việc gắn Order trực tiếp vào Table.
 * Một Session đại diện cho một lần phục vụ - từ khi khách ngồi xuống đến khi
 * thanh toán.
 * </p>
 * 
 * <p>
 * <b>Quan hệ:</b>
 * </p>
 * <ul>
 * <li>Session → Order (1-N): Session sở hữu các Order</li>
 * <li>Session → Table (1-N): Session quản lý nhiều Table (gộp bàn)</li>
 * <li>Order KHÔNG trực tiếp liên kết với Table</li>
 * <li>Table chỉ quan tâm: có thuộc Session nào không?</li>
 * </ul>
 * 
 * <p>
 * <b>Nghiệp vụ đơn giản:</b>
 * </p>
 * <ul>
 * <li>✅ Attach Table: Thêm bàn vào session (gộp bàn)</li>
 * <li>✅ Detach Table: Bỏ bàn khỏi session (chỉ khi session còn > 1 bàn)</li>
 * <li>❌ KHÔNG hỗ trợ: Split Bill, Transfer Session phức tạp</li>
 * </ul>
 * 
 * <p>
 * <b>Invariants:</b>
 * </p>
 * <ul>
 * <li>Session LUÔN có ít nhất 1 Table</li>
 * <li>Table chỉ thuộc tối đa 1 Session ACTIVE</li>
 * <li>Sau khi thanh toán (COMPLETED), tất cả Table được giải phóng</li>
 * </ul>
 * 
 * <p>
 * <b>Vòng đời:</b> PENDING → ACTIVE → COMPLETED/CANCELLED
 * </p>
 */
@Entity
@Table(name = "serving_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE serving_sessions SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class ServingSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(length = 500)
    private String note;

    // Session sở hữu nhiều Order
    // Lưu ý: KHÔNG hỗ trợ split bill - chỉ có 1 Order chính cho mỗi Session
    @Builder.Default
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL)
    private Set<Order> orders = new HashSet<>();

    // Session quản lý nhiều Table (M-1 từ Table → Session)
    // Hỗ trợ gộp bàn thông qua Attach/Detach
    @Builder.Default
    @OneToMany(mappedBy = "currentSession")
    private Set<DiningTable> tables = new HashSet<>();

    public enum SessionStatus {
        PENDING, // Khách tạo qua QR, chờ nhân viên xác nhận
        ACTIVE, // Đang phục vụ (nhân viên đã xác nhận hoặc tạo trực tiếp)
        COMPLETED, // Đã thanh toán xong
        CANCELLED // Đã hủy
    }

    /**
     * Lấy bàn chính của session (bàn đầu tiên được mở).
     * Dùng để hiển thị trên UI và in hóa đơn.
     */
    public DiningTable getPrimaryTable() {
        if (tables == null || tables.isEmpty())
            return null;
        return tables.iterator().next();
    }

    /**
     * Lấy tên các bàn trong session, ngăn cách bởi dấu phẩy.
     * VD: "Bàn 01, Bàn 02, Bàn 03"
     */
    public String getTableNames() {
        if (tables == null || tables.isEmpty())
            return "";
        return tables.stream()
                .map(DiningTable::getName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Lấy order chính (order đầu tiên đang OPEN).
     * Với split bill, có thể có nhiều order.
     */
    public Order getPrimaryOrder() {
        if (orders == null || orders.isEmpty())
            return null;
        return orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.OPEN)
                .findFirst()
                .orElseGet(() -> orders.iterator().next());
    }
}
