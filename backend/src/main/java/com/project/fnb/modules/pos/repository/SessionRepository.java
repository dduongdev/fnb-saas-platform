package com.project.fnb.modules.pos.repository;

import com.project.fnb.modules.pos.entity.ServingSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho quản lý ServingSession entities.
 * 
 * <p><b>Custom Queries:</b> Chứa các query tối ưu với JOIN FETCH để tránh N+1 problem
 * khi load session với tables, orders, items, products.</p>
 * 
 * <p><b>Performance:</b> Tất cả queries sử dụng LEFT JOIN FETCH để eager load relations
 * trong 1 query duy nhất, giảm số lượng database roundtrips.</p>
 * 
 * @see ServingSession
 */
public interface SessionRepository extends JpaRepository<ServingSession, Long> {

    /**
     * Tìm session ACTIVE theo ID với tất cả relations (tables, orders, items, products).
     * 
     * <p><b>Use Case:</b> Lấy thông tin đầy đủ session để hiển thị trong POS UI.</p>
     * 
     * <p><b>Performance:</b> Eager load tất cả relations trong 1 query để tránh N+1.</p>
     * 
     * @param id Session ID
     * @return Optional&lt;ServingSession&gt; - Empty nếu session không tồn tại hoặc không ACTIVE
     */
    @Query("SELECT s FROM ServingSession s " +
           "LEFT JOIN FETCH s.tables t " +
           "LEFT JOIN FETCH s.orders o " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.product " +
           "WHERE s.id = :id AND s.status = 'ACTIVE'")
    Optional<ServingSession> findActiveByIdWithDetails(@Param("id") Long id);

    /**
     * Tìm session ACTIVE của một bàn.
     * 
     * <p><b>Business Rule:</b> Mỗi bàn chỉ có thể thuộc 1 session ACTIVE duy nhất.</p>
     * 
     * <p><b>Use Case:</b> Kiểm tra bàn có khách hay không trước khi mở session mới.</p>
     * 
     * @param tableId ID của bàn cần kiểm tra (UUID)
     * @return Optional&lt;ServingSession&gt; - Empty nếu bàn trống
     */
    @Query("SELECT s FROM ServingSession s " +
           "JOIN s.tables t " +
           "WHERE t.id = :tableId AND s.status = 'ACTIVE'")
    Optional<ServingSession> findActiveByTableId(@Param("tableId") String tableId);

    /**
     * Tìm session theo ID với đầy đủ thông tin (bất kể status).
     * 
     * <p><b>Use Case:</b> Xem chi tiết session đã hoàn thành hoặc bị hủy.</p>
     * 
     * @param id Session ID
     * @return Optional&lt;ServingSession&gt; với tất cả relations
     */
    @Query("SELECT s FROM ServingSession s " +
           "LEFT JOIN FETCH s.tables " +
           "LEFT JOIN FETCH s.orders o " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.product " +
           "WHERE s.id = :id")
    Optional<ServingSession> findByIdWithDetails(@Param("id") Long id);

    /**
     * Lấy tất cả session ACTIVE của tenant hiện tại.
     * 
     * <p><b>Use Case:</b> Hiển thị danh sách session đang hoạt động trong POS UI.</p>
     * 
     * <p><b>Note:</b> Query sử dụng DISTINCT để tránh duplicate do JOIN FETCH.</p>
     * 
     * @return Danh sách ServingSession ACTIVE
     */
    @Query("SELECT DISTINCT s FROM ServingSession s " +
           "LEFT JOIN FETCH s.tables " +
           "WHERE s.status = 'ACTIVE'")
    List<ServingSession> findAllActive();

    /**
     * Lấy tất cả session PENDING (đang chờ xác nhận từ nhân viên).
     * 
     * <p><b>Context:</b> Khi khách order qua QR code, session được tạo với status PENDING.
     * Staff sẽ xem danh sách này để Confirm hoặc Reject.</p>
     * 
     * <p><b>Sort Order:</b> Sắp xếp theo startedAt ASC (order cũ nhất lên đầu) để ưu tiên xử lý.</p>
     * 
     * <p><b>Performance:</b> Eager load tất cả relations để hiển thị đầy đủ thông tin trong UI.</p>
     * 
     * @return Danh sách ServingSession PENDING, sắp xếp theo thời gian tạo
     */
    @Query("SELECT DISTINCT s FROM ServingSession s " +
           "LEFT JOIN FETCH s.tables t " +
           "LEFT JOIN FETCH s.orders o " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.product " +
           "WHERE s.status = 'PENDING' " +
           "ORDER BY s.startedAt ASC")
    List<ServingSession> findPendingSessions();

    /**
     * Lấy tất cả session ACTIVE với đầy đủ thông tin.
     * 
     * <p><b>Use Case:</b> Hiển thị danh sách session đang hoạt động trong POS dashboard.</p>
     * 
     * <p><b>Sort Order:</b> Sắp xếp theo startedAt DESC (mới nhất lên đầu).</p>
     * 
     * <p><b>Performance:</b> Eager load relations để tránh N+1 khi render UI.</p>
     * 
     * @return Danh sách ServingSession ACTIVE với tables, orders, items, products
     */
    @Query("SELECT DISTINCT s FROM ServingSession s " +
           "LEFT JOIN FETCH s.tables t " +
           "LEFT JOIN FETCH s.orders o " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.product " +
           "WHERE s.status = 'ACTIVE' " +
           "ORDER BY s.startedAt DESC")
    List<ServingSession> findActiveSessions();

    @Query("SELECT s FROM ServingSession s " +
           "LEFT JOIN FETCH s.tables t " +
           "LEFT JOIN FETCH s.orders o " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.product " +
           "ORDER BY s.startedAt DESC")
    Page<ServingSession> findSessionHistory(Pageable pageable);
}
