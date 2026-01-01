package com.project.fnb.modules.pos.repository;

import com.project.fnb.modules.pos.entity.ServingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<ServingSession, Long> {

    /**
     * Tìm session đang active theo ID với tất cả relations.
     */
    @Query("SELECT s FROM ServingSession s " +
           "LEFT JOIN FETCH s.tables t " +
           "LEFT JOIN FETCH s.orders o " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.product " +
           "WHERE s.id = :id AND s.status = 'ACTIVE'")
    Optional<ServingSession> findActiveByIdWithDetails(@Param("id") Long id);

    /**
     * Tìm session đang active của một bàn.
     */
    @Query("SELECT s FROM ServingSession s " +
           "JOIN s.tables t " +
           "WHERE t.id = :tableId AND s.status = 'ACTIVE'")
    Optional<ServingSession> findActiveByTableId(@Param("tableId") Integer tableId);

    /**
     * Tìm session theo ID với đầy đủ thông tin.
     */
    @Query("SELECT s FROM ServingSession s " +
           "LEFT JOIN FETCH s.tables " +
           "LEFT JOIN FETCH s.orders o " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.product " +
           "WHERE s.id = :id")
    Optional<ServingSession> findByIdWithDetails(@Param("id") Long id);

    /**
     * Lấy tất cả session đang active của tenant.
     */
    @Query("SELECT DISTINCT s FROM ServingSession s " +
           "LEFT JOIN FETCH s.tables " +
           "WHERE s.status = 'ACTIVE'")
    List<ServingSession> findAllActive();

    /**
     * Lấy tất cả session đang PENDING (chờ xác nhận từ nhân viên).
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
     * Lấy tất cả session đang ACTIVE.
     */
    @Query("SELECT DISTINCT s FROM ServingSession s " +
           "LEFT JOIN FETCH s.tables t " +
           "LEFT JOIN FETCH s.orders o " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.product " +
           "WHERE s.status = 'ACTIVE' " +
           "ORDER BY s.startedAt DESC")
    List<ServingSession> findActiveSessions();
}
