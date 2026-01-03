package com.project.fnb.modules.pos.entity;

import com.project.fnb.common.BaseEntity;
import com.project.fnb.modules.hrm.entity.Employee;
import com.project.fnb.modules.menu.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * Entity đại diện cho một món ăn trong đơn hàng.
 * 
 * <p><b>Price Snapshot Pattern:</b> Giá được lưu snapshot tại thời điểm order
 * để đảm bảo tính nhất quán khi Product thay đổi giá sau này.</p>
 * 
 * <p><b>Original Table Tracking:</b> Lưu vết bàn gốc khi gọi món,
 * hữu ích cho trường hợp gộp bàn và có thể split bill theo bàn sau này.</p>
 * 
 * <p><b>Vòng đời OrderItem:</b></p>
 * <pre>
 * PENDING → SERVED (hoặc CANCELLED)
 * </pre>
 * 
 * <p><b>Business Rules:</b></p>
 * <ul>
 *   <li>Chỉ có thể xóa/sửa món có status = PENDING</li>
 *   <li>Món SERVED hoặc CANCELLED không thể thay đổi</li>
 *   <li>price là snapshot, không liên kết với Product.price hiện tại</li>
 *   <li>originalTable có thể null nếu nhân viên thêm món trực tiếp</li>
 * </ul>
 * 
 * @author FNB Team
 * @version 1.0
 * @see Order
 * @see Product
 * @see DiningTable
 */
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_table_id")
    private DiningTable originalTable;

    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal price;

    private String note;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private ItemStatus status = ItemStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    /**
     * Enum trạng thái của OrderItem.
     */
    public enum ItemStatus {
        /** Mới thêm vào, chưa được mang ra */
        PENDING,
        /** Đã mang ra cho khách */
        SERVED,
        /** Đã hủy */
        CANCELLED
    }
}