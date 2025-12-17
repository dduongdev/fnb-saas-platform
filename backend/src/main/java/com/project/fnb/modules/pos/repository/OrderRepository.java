package com.project.fnb.modules.pos.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.fnb.modules.pos.entity.Order;
import com.project.fnb.modules.reporting.dto.HourlyStatDto;

public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @Query("SELECT o FROM Order o " +
           "JOIN FETCH o.table " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.product p " +
           "WHERE o.table.id = :tableId AND o.status = :status")
    Optional<Order> findByTableIdAndStatusWithDetails(@Param("tableId") Integer tableId, 
                                                      @Param("status") Order.OrderStatus status);

    @Override
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findById(@Param("id") Long id);

    @Query("SELECT new com.project.fnb.modules.reporting.dto.HourlyStatDto(HOUR(o.createdAt), COUNT(o)) " +
           "FROM Order o " +
           "WHERE o.status = 'COMPLETED' " +
           "AND o.createdAt BETWEEN :start AND :end " +
           "GROUP BY HOUR(o.createdAt) " +
           "ORDER BY HOUR(o.createdAt) ASC")
    List<HourlyStatDto> findPeakHours(LocalDateTime start, LocalDateTime end);

    @Query(value = "SELECT * FROM orders WHERE id = :id", nativeQuery = true)
    Optional<Order> findByIdGlobal(@Param("id") Long id);
}