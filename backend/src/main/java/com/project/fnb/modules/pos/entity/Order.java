package com.project.fnb.modules.pos.entity;

import com.project.fnb.common.BaseEntity;
import com.project.fnb.modules.hrm.entity.Employee;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

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

    // Order thuộc về Session, KHÔNG thuộc trực tiếp Table
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
    private String paymentMethod; // CASH, MOMO...

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Employee createdBy; // Null nếu khách tự gọi

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private Set<OrderItem> items = new HashSet<>();

    public enum OrderStatus {
        OPEN, // Đang phục vụ
        WAITING_PAYMENT, // Chờ thanh toán
        COMPLETED, // Đã thanh toán xong
        CANCELLED // Hủy
    }

    /**
     * Lấy bàn chính của order (từ session).
     */
    public DiningTable getPrimaryTable() {
        return session != null ? session.getPrimaryTable() : null;
    }
}