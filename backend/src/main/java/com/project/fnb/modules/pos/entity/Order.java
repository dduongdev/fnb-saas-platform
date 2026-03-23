package com.project.fnb.modules.pos.entity;

import com.project.fnb.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity đại diện cho đơn hàng trong hệ thống POS.
 * 
 * <p><b>Session-based Model:</b> Order thuộc về Session, KHÔNG gắn trực tiếp vào Table.
 * Điều này cho phép linh hoạt trong việc quản lý gộp bàn và phục vụ nhóm khách.</p>
 * 
 * <p><b>Vòng đời Order:</b></p>
 * <pre>
 * OPEN → WAITING_PAYMENT → COMPLETED
 *                        → CANCELLED
 * </pre>
 * 
 * <p><b>Quan hệ:</b></p>
 * <ul>
 *   <li>Mỗi Order thuộc 1 Session</li>
 *   <li>Mỗi Order chứa nhiều OrderItems</li>
 * </ul>
 * 
 * <p><b>Business Rules:</b></p>
 * <ul>
 *   <li>totalAmount được tính tự động khi thêm/xóa items</li>
 *   <li>Chỉ OPEN order mới cho phép thêm/xóa items</li>
 *   <li>paymentMethod chỉ được set khi thanh toán (CASH, VNPAY, MOMO...)</li>
 * </ul>
 * 
 * @author FNB Team
 * @version 1.0
 * @see ServingSession
 * @see OrderItem
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE orders SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ServingSession session;

    @Column(name = "total_amount")
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.OPEN;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private Set<OrderItem> items = new HashSet<>();

    /**
     * Enum trạng thái của Order.
     */
    public enum OrderStatus {
        /** Đang mở - cho phép thêm/xóa items */
        OPEN,
        /** Chờ thanh toán - khách đã yêu cầu thanh toán */
        WAITING_PAYMENT,
        /** Đã thanh toán xong */
        COMPLETED,
        /** Đã hủy */
        CANCELLED
    }

    /**
     * Lấy bàn chính của order (từ session).
     * 
     * @return DiningTable chính, hoặc null nếu session không có bàn
     */
    public DiningTable getPrimaryTable() {
        return session != null ? session.getPrimaryTable() : null;
    }
}