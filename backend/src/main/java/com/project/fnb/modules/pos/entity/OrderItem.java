package com.project.fnb.modules.pos.entity;

import com.project.fnb.common.BaseEntity;
import com.project.fnb.modules.hrm.entity.Employee;
import com.project.fnb.modules.menu.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class OrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Lưu vết món này được gọi từ bàn vật lý nào (trong trường hợp gộp bàn)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_table_id")
    private DiningTable originalTable;

    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal price; // Giá tại thời điểm order (Snapshot)

    private String note;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private ItemStatus status = ItemStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    public enum ItemStatus {
        PENDING, // Mới thêm vào
        SERVED,  // Đã ra món
        CANCELLED // Đã hủy
    }
}