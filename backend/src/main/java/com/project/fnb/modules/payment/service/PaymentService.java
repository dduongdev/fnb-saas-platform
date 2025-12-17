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
import com.project.fnb.modules.pos.entity.Order.OrderStatus;
import com.project.fnb.modules.pos.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final TenantRepository tenantRepository;
    private final PaymentStrategyFactory paymentStrategyFactory;
    private final PaymentTransactionRepository transactionRepository;

    @Transactional
    public String createPaymentUrl(Long orderId, String methodCode, String ipAddress) {
        // 1. Convert String -> Enum
        PaymentProvider provider = PaymentProvider.from(methodCode);
        if (provider == null) {
            throw new AppException(400, "Mã phương thức thanh toán không hợp lệ: " + methodCode);
        }

        // 2. Lấy Order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(404, "Order not found"));
        
        if (order.getStatus() == Order.OrderStatus.COMPLETED) {
            throw new AppException(400, "Đơn hàng này đã được thanh toán rồi.");
        }

        // 3. Lấy Tenant & Config
        Tenant tenant = tenantRepository.findById(order.getTenantId())
                .orElseThrow(() -> new AppException(404, "Tenant not found"));

        PaymentConfigDto config = tenant.getPaymentConfig();
        if (config == null) {
            throw new AppException(400, "Quán chưa cấu hình bất kỳ cổng thanh toán nào.");
        }

        // 4. Gọi Factory để lấy Strategy tương ứng
        // (Ví dụ: methodCode="VNPAY" -> Trả về VnPayStrategy)
        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(provider);

        String txnRef = order.getId() + "_" + System.currentTimeMillis();

        order.setStatus(OrderStatus.WAITING_PAYMENT);
        orderRepository.save(order);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .order(order)
                .amount(order.getTotalAmount())
                .provider(provider)
                .transactionRef(txnRef) // Gửi mã này sang VNPay
                .status(PaymentTransaction.TransactionStatus.PENDING)
                .build();
        
        transactionRepository.save(transaction);
        
        // 5. Thực thi logic tạo URL
        return strategy.createPaymentUrl(order, config, ipAddress, txnRef);
    }
}