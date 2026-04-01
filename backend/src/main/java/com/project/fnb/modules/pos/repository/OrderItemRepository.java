package com.project.fnb.modules.pos.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.fnb.modules.pos.entity.OrderItem;
import com.project.fnb.modules.reporting.dto.TopProductDto;

/**
 * Repository cho quản lý OrderItem entities.
 * 
 * <p><b>Custom Queries:</b> Chứa query để thống kê sản phẩm bán chạy (top selling products).</p>
 * 
 * @see OrderItem
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    
    /**
     * Tìm OrderItem theo ID (tenant-scoped).
     * 
     * <p><b>Tenant Isolation:</b> Tự động filter theo tenantId qua Hibernate filter.</p>
     * 
     * @param id OrderItem ID
     * @return Optional&lt;OrderItem&gt;
     */
    @Override
    @Query("SELECT i FROM OrderItem i WHERE i.id = :id")
    Optional<OrderItem> findById(@Param("id") Long id);

    /**
     * Thống kê top sản phẩm bán chạy trong khoảng thời gian.
     * 
     * <p><b>Ranking Logic:</b> Sắp xếp theo tổng số lượng bán (SUM(quantity)) giảm dần.</p>
     * 
     * <p><b>Filter:</b> Chỉ đếm items thuộc orders COMPLETED (bỏ qua CANCELLED).</p>
     * 
     * <p><b>Use Case:</b> Hiển thị top 10 món bán chạy trong báo cáo doanh thu.</p>
     * 
     * @param start Thời điểm bắt đầu
     * @param end Thời điểm kết thúc
     * @param pageable Pageable để giới hạn số kết quả (VD: top 10)
     * @return Danh sách TopProductDto chứa (productId, productName, totalQuantity, totalRevenue)
     */
    @Query("SELECT new com.project.fnb.modules.reporting.dto.TopProductDto(" +
            "oi.product.id, oi.product.name, " +
            "SUM(CASE WHEN oi.status IN ('PENDING','SERVED') THEN oi.quantity ELSE 0 END), " +
            "SUM(CASE WHEN oi.status IN ('PENDING','SERVED') THEN oi.price * oi.quantity ELSE 0 END), " +
            "SUM(CASE WHEN oi.status = 'CANCELLED' THEN oi.quantity ELSE 0 END), " +
            "SUM(CASE WHEN oi.status = 'CANCELLED' THEN oi.price * oi.quantity ELSE 0 END)) " +
            "FROM OrderItem oi " +
            "JOIN oi.order o " +
            "WHERE o.status = 'COMPLETED' " +
            "AND o.completedAt BETWEEN :start AND :end " +
            "GROUP BY oi.product.id, oi.product.name " +
            "ORDER BY SUM(CASE WHEN oi.status IN ('PENDING','SERVED') THEN oi.quantity ELSE 0 END) DESC")
    List<TopProductDto> findTopSellingProducts(LocalDateTime start, LocalDateTime end, Pageable pageable);
}
