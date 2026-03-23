package com.project.fnb.modules.payment.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.modules.global.dto.PaymentConfigDto;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.payment.entity.PaymentTransaction;
import com.project.fnb.modules.payment.enums.PaymentProvider;
import com.project.fnb.modules.payment.repository.PaymentTransactionRepository;
import com.project.fnb.modules.payment.strategy.PaymentStrategy;
import com.project.fnb.modules.payment.strategy.PaymentStrategyFactory;
import com.project.fnb.modules.pos.entity.Order;
import com.project.fnb.modules.pos.entity.ServingSession;
import com.project.fnb.modules.pos.repository.OrderRepository;
import com.project.fnb.modules.pos.repository.SessionRepository;
import com.project.fnb.modules.pos.service.SessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PaymentStrategyFactory paymentStrategyFactory;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private PaymentStrategy paymentStrategy;

    @InjectMocks
    private PaymentService paymentService;

    // --- createPaymentUrl Tests ---

    @Test
    void createPaymentUrl_InvalidMethodCode_ShouldThrowException() {
        AppException ex = assertThrows(AppException.class, () ->
                paymentService.createPaymentUrl(1L, "INVALID_CODE", "127.0.0.1"));

        assertEquals(400, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("không hợp lệ"));
    }

    @Test
    void createPaymentUrl_OrderNotFoundAndSessionNotFound_ShouldThrowException() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());
        when(sessionRepository.findById(1L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                paymentService.createPaymentUrl(1L, "VNPAY", "127.0.0.1"));

        assertEquals(404, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Không tìm thấy Đơn hàng hoặc Session"));
    }

    @Test
    void createPaymentUrl_SessionWithNoOrders_ShouldThrowException() {
        ServingSession session = new ServingSession();
        session.setOrders(Collections.emptySet());

        when(orderRepository.findById(1L)).thenReturn(Optional.empty());
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        AppException ex = assertThrows(AppException.class, () ->
                paymentService.createPaymentUrl(1L, "VNPAY", "127.0.0.1"));

        assertEquals(400, ex.getErrorCode());
        assertEquals("Session chưa có đơn hàng nào.", ex.getMessage());
    }

    @Test
    void createPaymentUrl_OrderCompleted_ShouldThrowException() {
        Order order = new Order();
        order.setStatus(Order.OrderStatus.COMPLETED);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        AppException ex = assertThrows(AppException.class, () ->
                paymentService.createPaymentUrl(1L, "VNPAY", "127.0.0.1"));

        assertEquals(400, ex.getErrorCode());
        assertEquals("Đơn hàng này đã được thanh toán rồi.", ex.getMessage());
    }

    @Test
    void createPaymentUrl_TenantNoConfig_ShouldThrowException() {
        Order order = new Order();
        order.setTenantId("tenant1");
        order.setStatus(Order.OrderStatus.OPEN);

        Tenant tenant = Tenant.builder().id("tenant1").paymentConfig(null).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(tenantRepository.findById("tenant1")).thenReturn(Optional.of(tenant));

        AppException ex = assertThrows(AppException.class, () ->
                paymentService.createPaymentUrl(1L, "VNPAY", "127.0.0.1"));

        assertEquals(400, ex.getErrorCode());
        assertEquals("Quán chưa cấu hình bất kỳ cổng thanh toán nào.", ex.getMessage());
    }

    @Test
    void createPaymentUrl_ValidInput_ShouldReturnUrl() {
        Order order = new Order();
        order.setId(100L);
        order.setTenantId("tenant1");
        order.setStatus(Order.OrderStatus.OPEN);
        order.setTotalAmount(new BigDecimal("50000"));

        PaymentConfigDto config = new PaymentConfigDto();
        Tenant tenant = Tenant.builder().id("tenant1").paymentConfig(config).build();

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(tenantRepository.findById("tenant1")).thenReturn(Optional.of(tenant));
        when(paymentStrategyFactory.getStrategy(PaymentProvider.VNPAY)).thenReturn(paymentStrategy);
        when(paymentStrategy.createPaymentUrl(any(), any(), eq("127.0.0.1"), anyString()))
                .thenReturn("http://vnpay.url/pay");

        String result = paymentService.createPaymentUrl(100L, "VNPAY", "127.0.0.1");

        assertEquals("http://vnpay.url/pay", result);
        assertEquals(Order.OrderStatus.WAITING_PAYMENT, order.getStatus());
        verify(orderRepository).save(order);

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactionRepository).save(captor.capture());
        PaymentTransaction savedTxn = captor.getValue();
        assertEquals(new BigDecimal("50000"), savedTxn.getAmount());
        assertEquals(PaymentProvider.VNPAY, savedTxn.getProvider());
        assertTrue(savedTxn.getTransactionRef().startsWith("100_"));
    }
    
    @Test
    void createPaymentUrl_FromSessionValidInput_ShouldReturnUrl() {
        Order order1 = new Order();
        order1.setId(101L);
        order1.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        order1.setTenantId("tenant1");
        order1.setStatus(Order.OrderStatus.OPEN);
        order1.setTotalAmount(new BigDecimal("30000"));
        
        Order order2 = new Order();
        order2.setId(102L);
        order2.setCreatedAt(LocalDateTime.now()); // newest
        order2.setTenantId("tenant1");
        order2.setStatus(Order.OrderStatus.OPEN);
        order2.setTotalAmount(new BigDecimal("20000"));

        ServingSession session = new ServingSession();
        Set<Order> orders = new HashSet<>();
        orders.add(order1);
        orders.add(order2);
        session.setOrders(orders);

        when(orderRepository.findById(1L)).thenReturn(Optional.empty());
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        
        PaymentConfigDto config = new PaymentConfigDto();
        Tenant tenant = Tenant.builder().id("tenant1").paymentConfig(config).build();
        when(tenantRepository.findById("tenant1")).thenReturn(Optional.of(tenant));
        when(paymentStrategyFactory.getStrategy(PaymentProvider.VNPAY)).thenReturn(paymentStrategy);
        when(paymentStrategy.createPaymentUrl(any(), any(), eq("127.0.0.1"), anyString()))
                .thenReturn("http://vnpay.url/sessionpay");

        String result = paymentService.createPaymentUrl(1L, "VNPAY", "127.0.0.1");

        assertEquals("http://vnpay.url/sessionpay", result);
        assertEquals(Order.OrderStatus.WAITING_PAYMENT, order2.getStatus());
        verify(orderRepository).save(order2);
    }

    // --- processPaymentCallback Tests ---

    @Test
    void processPaymentCallback_MissingTxnRef_ShouldThrowException() {
        Map<String, String> requestParams = new HashMap<>();

        AppException ex = assertThrows(AppException.class, () ->
                paymentService.processPaymentCallback(requestParams));

        assertEquals(400, ex.getErrorCode());
        assertEquals("Missing TxnRef", ex.getMessage());
    }

    @Test
    void processPaymentCallback_TransactionNotFound_ShouldThrowException() {
        Map<String, String> requestParams = new HashMap<>();
        requestParams.put("vnp_TxnRef", "123_abc");

        when(transactionRepository.findByTransactionRef("123_abc")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                paymentService.processPaymentCallback(requestParams));

        assertEquals(404, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Transaction not found"));
    }

    @Test
    void processPaymentCallback_InvalidSignature_ShouldThrowException() {
        Map<String, String> requestParams = new HashMap<>();
        requestParams.put("vnp_TxnRef", "123_abc");

        Order order = new Order();
        order.setTenantId("tenant1");

        PaymentTransaction txn = new PaymentTransaction();
        txn.setOrder(order);
        txn.setProvider(PaymentProvider.VNPAY);

        PaymentConfigDto config = new PaymentConfigDto();
        Tenant tenant = Tenant.builder().id("tenant1").paymentConfig(config).build();

        when(transactionRepository.findByTransactionRef("123_abc")).thenReturn(Optional.of(txn));
        when(tenantRepository.findById("tenant1")).thenReturn(Optional.of(tenant));
        when(paymentStrategyFactory.getStrategy(PaymentProvider.VNPAY)).thenReturn(paymentStrategy);
        when(paymentStrategy.verifyPayment(requestParams, config)).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () ->
                paymentService.processPaymentCallback(requestParams));

        assertEquals(400, ex.getErrorCode());
        assertEquals("Invalid Checksum/Signature", ex.getMessage());
    }

    @Test
    void processPaymentCallback_FailedResponseCode_ShouldUpdateStatusAndThrow() {
        Map<String, String> requestParams = new HashMap<>();
        requestParams.put("vnp_TxnRef", "123_abc");
        requestParams.put("vnp_ResponseCode", "24");
        requestParams.put("vnp_TransactionNo", "txn123");

        Order order = new Order();
        order.setTenantId("tenant1");

        PaymentTransaction txn = new PaymentTransaction();
        txn.setOrder(order);
        txn.setProvider(PaymentProvider.VNPAY);

        PaymentConfigDto config = new PaymentConfigDto();
        Tenant tenant = Tenant.builder().id("tenant1").paymentConfig(config).build();

        when(transactionRepository.findByTransactionRef("123_abc")).thenReturn(Optional.of(txn));
        when(tenantRepository.findById("tenant1")).thenReturn(Optional.of(tenant));
        when(paymentStrategyFactory.getStrategy(PaymentProvider.VNPAY)).thenReturn(paymentStrategy);
        when(paymentStrategy.verifyPayment(requestParams, config)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () ->
                paymentService.processPaymentCallback(requestParams));

        assertEquals(400, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Payment Failed at Gateway"));

        assertEquals("txn123", txn.getGatewayTransactionId());
        assertEquals("24", txn.getResponseCode());
        assertEquals(PaymentTransaction.TransactionStatus.FAILED, txn.getStatus());
        verify(transactionRepository).save(txn);
    }

    @Test
    void processPaymentCallback_SuccessResponseCode_ShouldProcessAndReturnSuccess() {
        Map<String, String> requestParams = new HashMap<>();
        requestParams.put("vnp_TxnRef", "123_abc");
        requestParams.put("vnp_ResponseCode", "00");
        requestParams.put("vnp_TransactionNo", "txn123");

        Order order = new Order();
        order.setId(123L);
        order.setTenantId("tenant1");

        PaymentTransaction txn = new PaymentTransaction();
        txn.setOrder(order);
        txn.setProvider(PaymentProvider.VNPAY);

        PaymentConfigDto config = new PaymentConfigDto();
        Tenant tenant = Tenant.builder().id("tenant1").paymentConfig(config).build();

        when(transactionRepository.findByTransactionRef("123_abc")).thenReturn(Optional.of(txn));
        when(tenantRepository.findById("tenant1")).thenReturn(Optional.of(tenant));
        when(paymentStrategyFactory.getStrategy(PaymentProvider.VNPAY)).thenReturn(paymentStrategy);
        when(paymentStrategy.verifyPayment(requestParams, config)).thenReturn(true);

        String result = paymentService.processPaymentCallback(requestParams);

        assertEquals("SUCCESS", result);
        assertEquals(PaymentTransaction.TransactionStatus.SUCCESS, txn.getStatus());
        verify(transactionRepository).save(txn);
        verify(sessionService).handlePaymentSuccess(123L, "txn123");
    }
}
