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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock
    private DailyStatRepository dailyStatRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ReportingService reportingService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void handleOrderPaid_WithExistingDailyStat_ShouldUpdateAndSave() {
        Order order = new Order();
        order.setTenantId("tenant123");
        order.setTotalAmount(new BigDecimal("150.00"));

        OrderPaidEvent event = new OrderPaidEvent(order);

        DailyStat existingStat = DailyStat.builder()
                .reportDate(LocalDate.now())
                .totalRevenue(new BigDecimal("500.00"))
                .totalOrders(5)
                .build();

        when(dailyStatRepository.findByReportDate(LocalDate.now())).thenReturn(Optional.of(existingStat));

        reportingService.handleOrderPaid(event);

        assertNull(TenantContext.getTenantId()); // Should be cleared

        ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
        verify(dailyStatRepository).save(captor.capture());

        DailyStat savedStat = captor.getValue();
        assertEquals(new BigDecimal("650.00"), savedStat.getTotalRevenue());
        assertEquals(6, savedStat.getTotalOrders());
    }

    @Test
    void handleOrderPaid_WithNoExistingDailyStat_ShouldCreateNewAndSave() {
        Order order = new Order();
        order.setTenantId("tenant123");
        order.setTotalAmount(new BigDecimal("150.00"));

        OrderPaidEvent event = new OrderPaidEvent(order);

        when(dailyStatRepository.findByReportDate(LocalDate.now())).thenReturn(Optional.empty());

        reportingService.handleOrderPaid(event);

        assertNull(TenantContext.getTenantId()); // Should be cleared

        ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
        verify(dailyStatRepository).save(captor.capture());

        DailyStat savedStat = captor.getValue();
        assertEquals(LocalDate.now(), savedStat.getReportDate());
        assertEquals(new BigDecimal("150.00"), savedStat.getTotalRevenue());
        assertEquals(1, savedStat.getTotalOrders());
    }

    @Test
    void handleOrderPaid_WithException_ShouldClearContext() {
        Order order = new Order();
        order.setTenantId("tenant123");

        OrderPaidEvent event = new OrderPaidEvent(order);

        when(dailyStatRepository.findByReportDate(any(LocalDate.class))).thenThrow(new RuntimeException("Database error"));

        // Exception should be caught and logged
        assertDoesNotThrow(() -> reportingService.handleOrderPaid(event));

        assertNull(TenantContext.getTenantId()); // Should be cleared, finally block executed
    }

    @Test
    void getRevenueReport_ShouldReturnDtoList() {
        LocalDate from = LocalDate.now().minusDays(2);
        LocalDate to = LocalDate.now();

        DailyStat stat = DailyStat.builder()
                .reportDate(from)
                .totalRevenue(new BigDecimal("200"))
                .totalOrders(2)
                .build();

        when(dailyStatRepository.findByReportDateBetweenOrderByReportDateAsc(from, to))
                .thenReturn(List.of(stat));

        List<RevenueReportDto> result = reportingService.getRevenueReport(from, to);

        assertEquals(1, result.size());
        assertEquals(from, result.get(0).getDate());
        assertEquals(new BigDecimal("200"), result.get(0).getTotalRevenue());
        assertEquals(2, result.get(0).getOrderCount());
    }

    @Test
    void getTopSelling_ShouldInvokeRepository() {
        LocalDate from = LocalDate.now().minusDays(2);
        LocalDate to = LocalDate.now();
        int limit = 5;

        List<TopProductDto> mockedResult = List.of(mock(TopProductDto.class));

        when(orderItemRepository.findTopSellingProducts(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(mockedResult);

        List<TopProductDto> result = reportingService.getTopSelling(from, to, limit);

        assertEquals(1, result.size());
        verify(orderItemRepository).findTopSellingProducts(
                eq(from.atStartOfDay()),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, limit))
        );
    }

    @Test
    void getPeakHours_ShouldInvokeRepository() {
        LocalDate from = LocalDate.now().minusDays(2);
        LocalDate to = LocalDate.now();

        List<HourlyStatDto> mockedResult = List.of(mock(HourlyStatDto.class));

        when(orderRepository.findPeakHours(
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(mockedResult);

        List<HourlyStatDto> result = reportingService.getPeakHours(from, to);

        assertEquals(1, result.size());
        verify(orderRepository).findPeakHours(
                eq(from.atStartOfDay()),
                any(LocalDateTime.class)
        );
    }
}
