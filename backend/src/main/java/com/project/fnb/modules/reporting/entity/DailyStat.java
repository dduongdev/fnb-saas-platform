package com.project.fnb.modules.reporting.entity;

import com.project.fnb.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
    name = "daily_stats",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "report_date"})
    },
    indexes = {
        @Index(name = "idx_dailystat_tenant_date", columnList = "tenant_id, report_date")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class DailyStat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "total_revenue")
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "total_orders")
    @Builder.Default
    private Integer totalOrders = 0;
}