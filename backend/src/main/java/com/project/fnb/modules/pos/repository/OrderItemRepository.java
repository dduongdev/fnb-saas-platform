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

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    @Override
    @Query("SELECT i FROM OrderItem i WHERE i.id = :id")
    Optional<OrderItem> findById(@Param("id") Long id);

    @Query("SELECT new com.project.fnb.modules.reporting.dto.TopProductDto(" +
            "oi.product.id, oi.product.name, SUM(oi.quantity), SUM(oi.price * oi.quantity)) " +
            "FROM OrderItem oi " +
            "JOIN oi.order o " +
            "WHERE o.status = 'COMPLETED' " +
            "AND o.completedAt BETWEEN :start AND :end " +
            "GROUP BY oi.product.id, oi.product.name " +
            "ORDER BY SUM(oi.quantity) DESC")
    List<TopProductDto> findTopSellingProducts(LocalDateTime start, LocalDateTime end, Pageable pageable);
}
