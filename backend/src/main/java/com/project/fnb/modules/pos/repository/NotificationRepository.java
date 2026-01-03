package com.project.fnb.modules.pos.repository;

import com.project.fnb.modules.pos.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository cho quản lý Notification entities.
 * 
 * <p><b>Queries:</b> Hỗ trợ lấy notifications theo các tiêu chí:
 * type, priority, read status, date range.</p>
 * 
 * @see Notification
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Lấy tất cả notifications sắp xếp theo thời gian mới nhất
     */
    @Query("SELECT n FROM Notification n ORDER BY n.createdAt DESC")
    Page<Notification> findAllByTenantOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Lấy notifications chưa đọc
     */
    @Query("SELECT n FROM Notification n WHERE n.isRead = false ORDER BY n.createdAt DESC")
    List<Notification> findUnreadNotifications();

    /**
     * Đếm số notifications chưa đọc
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.isRead = false")
    Long countUnreadNotifications();

    /**
     * Lấy notifications theo type
     */
    @Query("SELECT n FROM Notification n WHERE n.type = :type ORDER BY n.createdAt DESC")
    Page<Notification> findByType(@Param("type") String type, Pageable pageable);

    /**
     * Lấy notifications theo priority
     */
    @Query("SELECT n FROM Notification n WHERE n.priority = :priority ORDER BY n.createdAt DESC")
    Page<Notification> findByPriority(@Param("priority") Notification.Priority priority, Pageable pageable);

    /**
     * Lấy notifications trong khoảng thời gian
     */
    @Query("SELECT n FROM Notification n WHERE n.createdAt BETWEEN :startDate AND :endDate ORDER BY n.createdAt DESC")
    Page<Notification> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Đánh dấu notification đã đọc
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.id = :id")
    void markAsRead(@Param("id") Long id, @Param("readAt") LocalDateTime readAt);

    /**
     * Đánh dấu tất cả notifications đã đọc
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.isRead = false")
    void markAllAsRead(@Param("readAt") LocalDateTime readAt);

    /**
     * Lấy notifications gần đây (để hiển thị trong dropdown)
     */
    @Query("SELECT n FROM Notification n ORDER BY n.createdAt DESC")
    List<Notification> findRecentNotifications(Pageable pageable);

    /**
     * Xóa notifications cũ (để cleanup)
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :before")
    void deleteOldNotifications(@Param("before") LocalDateTime before);
}
