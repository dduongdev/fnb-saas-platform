package com.project.fnb.modules.pos.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.fnb.modules.pos.entity.Order;
import com.project.fnb.modules.reporting.dto.HourlyStatDto;

/**
 * Repository cho quản lý Order entities.
 * 
 * <p><b>Custom Queries:</b> Chứa các query cho reporting (peak hours analysis).</p>
 * 
 * <p><b>Multi-tenancy:</b> Override findById để đảm bảo tenant isolation,
 * ngoại trừ findByIdGlobal (native query) dùng cho system admin.</p>
 * 
 * @see Order
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Tìm order theo ID (tenant-scoped).
     * 
     * <p><b>Tenant Isolation:</b> Query này tự động filter theo tenantId qua Hibernate filter.</p>
     * 
     * @param id Order ID
     * @return Optional&lt;Order&gt;
     */
    @Override
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findById(@Param("id") Long id);

    /**
     * Thống kê số lượng order theo giờ trong khoảng thời gian (peak hours analysis).
     * 
     * <p><b>Use Case:</b> Phân tích giờ cao điểm để sắp xếp nhân sự và nguyên liệu.</p>
     * 
     * <p><b>Filter:</b> Chỉ đếm order COMPLETED (bỏ qua CANCELLED).</p>
     * 
     * @param start Thời điểm bắt đầu
     * @param end Thời điểm kết thúc
     * @return Danh sách HourlyStatDto chứa (hour, orderCount), sắp xếp theo giờ
     */
    @Query("SELECT new com.project.fnb.modules.reporting.dto.HourlyStatDto(HOUR(o.createdAt), COUNT(o)) " +
           "FROM Order o " +
           "WHERE o.status = 'COMPLETED' " +
           "AND o.createdAt BETWEEN :start AND :end " +
           "GROUP BY HOUR(o.createdAt) " +
           "ORDER BY HOUR(o.createdAt) ASC")
    List<HourlyStatDto> findPeakHours(LocalDateTime start, LocalDateTime end);

    /**
     * Tìm order theo ID (global, không filter tenant) - Dùng cho system admin.
     * 
     * <p><b>WARNING:</b> Query này bypass tenant isolation. Chỉ dùng cho admin endpoints.</p>
     * 
     * @param id Order ID
     * @return Optional&lt;Order&gt;
     */
    @Query(value = "SELECT * FROM orders WHERE id = :id", nativeQuery = true)
    Optional<Order> findByIdGlobal(@Param("id") Long id);

    /**
     * Tìm order theo ID với items và products (eager load).
     * 
     * <p><b>Purpose:</b> Fix N+1 query issue trong SessionService.buildCustomerOrderResponse().
     * Đầy đủ JOIN FETCH chain: order → items → product.</p>
     * 
     * <p><b>Performance:</b> 1 query thay vì 1 + N queries (N = số items trong order).</p>
     * 
     * @param id Order ID
     * @return Optional&lt;Order&gt; với tất cả items + products
     */
    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.items oi " +
           "LEFT JOIN FETCH oi.product " +
           "WHERE o.id = :id")
    Optional<Order> findByIdWithItemsAndProducts(@Param("id") Long id);
}