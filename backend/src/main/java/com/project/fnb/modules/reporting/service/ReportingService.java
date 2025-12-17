package com.project.fnb.modules.reporting.service;

import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.pos.entity.Order;
import com.project.fnb.modules.pos.event.OrderPaidEvent;
import com.project.fnb.modules.reporting.dto.HourlyStatDto;
import com.project.fnb.modules.reporting.dto.RevenueReportDto;
import com.project.fnb.modules.reporting.dto.TopProductDto;
import com.project.fnb.modules.reporting.entity.DailyStat;
import com.project.fnb.modules.reporting.repository.DailyStatRepository;
import com.project.fnb.modules.pos.repository.OrderItemRepository;
import com.project.fnb.modules.pos.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportingService {

    private final DailyStatRepository dailyStatRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;

    // --- 1. REAL-TIME AGGREGATION ---
    
    @Async // Chạy bất đồng bộ để không block luồng thanh toán
    @EventListener
    @Transactional
    public void handleOrderPaid(OrderPaidEvent event) {
        Order order = event.getOrder();
        String tenantId = order.getTenantId(); // BaseEntity phải có getter tenantId

        // Vì @Async chạy thread mới, ta phải set lại TenantContext thủ công để Hibernate Filter hoạt động đúng
        // Hoặc ta phải tắt Filter và query bằng tenantId (nhưng set Context an toàn hơn)
        TenantContext.setTenantId(tenantId);
        
        try {
            LocalDate today = LocalDate.now();
            
            // Tìm bản ghi hôm nay, nếu chưa có thì tạo mới
            DailyStat stat = dailyStatRepository.findByReportDate(today)
                    .orElseGet(() -> DailyStat.builder()
                            .reportDate(today)
                            .totalRevenue(BigDecimal.ZERO)
                            .totalOrders(0)
                            .build());

            // Cộng dồn doanh thu và số đơn
            stat.setTotalRevenue(stat.getTotalRevenue().add(order.getTotalAmount()));
            stat.setTotalOrders(stat.getTotalOrders() + 1);
            
            dailyStatRepository.save(stat);
            log.info("📊 Updated DailyStat for tenant {} date {}", tenantId, today);
            
        } catch (Exception e) {
            log.error("Failed to update daily stats", e);
        } finally {
            TenantContext.clear();
        }
    }

    // --- 2. REPORT API LOGIC ---

    // Báo cáo doanh thu (Mặc định 30 ngày gần nhất hoặc theo range)
    public List<RevenueReportDto> getRevenueReport(LocalDate from, LocalDate to) {
        List<DailyStat> stats = dailyStatRepository.findByReportDateBetweenOrderByReportDateAsc(from, to);
        
        return stats.stream()
                .map(s -> new RevenueReportDto(s.getReportDate(), s.getTotalRevenue(), s.getTotalOrders()))
                .collect(Collectors.toList());
    }

    // Top món bán chạy
    public List<TopProductDto> getTopSelling(LocalDate from, LocalDate to, int limit) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(LocalTime.MAX);
        return orderItemRepository.findTopSellingProducts(start, end, PageRequest.of(0, limit));
    }

    // Khung giờ đông khách
    public List<HourlyStatDto> getPeakHours(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(LocalTime.MAX);
        return orderRepository.findPeakHours(start, end);
    }
}