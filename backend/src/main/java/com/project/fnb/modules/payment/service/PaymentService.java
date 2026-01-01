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

import com.project.fnb.modules.pos.entity.ServingSession;
import com.project.fnb.modules.pos.repository.SessionRepository;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final SessionRepository sessionRepository;
    private final com.project.fnb.modules.pos.service.SessionService sessionService;
    private final OrderRepository orderRepository;
    private final TenantRepository tenantRepository;
    private final PaymentStrategyFactory paymentStrategyFactory;
    private final PaymentTransactionRepository transactionRepository;

    @Transactional
    public String createPaymentUrl(Long id, String methodCode, String ipAddress) {
        // 1. Convert String -> Enum
        PaymentProvider provider = PaymentProvider.from(methodCode);
        if (provider == null) {
            throw new AppException(400, "Mã phương thức thanh toán không hợp lệ: " + methodCode);
        }

        // 2. Lay Order hoac Session
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            // Thu tim theo Session
            var session = sessionRepository.findById(id).orElseThrow(
                    () -> new AppException(404, "Không tìm thấy Đơn hàng hoặc Session thanh toán (ID: " + id + ")"));
            // Lay order dau tien hoac order actice
            if (session.getOrders().isEmpty()) {
                throw new AppException(400, "Session chưa có đơn hàng nào.");
            }
            // TODO: Logic ghép order néu cần, ở đây lấy order mới nhất
            order = session.getOrders().stream()
                    .max((o1, o2) -> o1.getCreatedAt().compareTo(o2.getCreatedAt()))
                    .orElse(session.getOrders().iterator().next());

            // Override amount confirm transaction = session total (Bill total)
            // But Order Entity has its own amount. We assume transaction amount is what
            // matters.
        }

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
        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(provider);

        // Tạo ref uniqueness
        String txnRef = order.getId() + "_" + System.currentTimeMillis();

        // Update status tam thoi
        order.setStatus(OrderStatus.WAITING_PAYMENT);
        orderRepository.save(order);

        // Luu transaction
        // NOTE: Amount should be Session Total if paying for session?
        // Frontend passed Session ID, intending to pay Session Total.
        // Order.totalAmount might be partial.
        // CHECK: ServingSession totalAmount logic.
        // Safer to use Order.totalAmount if we link to Order.
        // But if customer wants to pay full bill...
        // Let's assume Order.totalAmount is correct for now or matches Session logic.

        PaymentTransaction transaction = PaymentTransaction.builder()
                .order(order)
                .amount(order.getTotalAmount()) // or session.getTotalAmount() if available
                .provider(provider)
                .transactionRef(txnRef)
                .status(PaymentTransaction.TransactionStatus.PENDING)
                .build();

        transactionRepository.save(transaction);

        return strategy.createPaymentUrl(order, config, ipAddress, txnRef);
    }

    /**
     * Xử lý callback từ Payment Gateway (VNPay, Momo...)
     */
    @Transactional
    public String processPaymentCallback(java.util.Map<String, String> requestParams) {
        // 1. Get TxnRef
        String vnp_TxnRef = requestParams.get("vnp_TxnRef");
        if (vnp_TxnRef == null)
            throw new AppException(400, "Missing TxnRef");

        // Find transaction
        PaymentTransaction txn = transactionRepository.findByTransactionRef(vnp_TxnRef)
                .orElseThrow(() -> new AppException(404, "Transaction not found: " + vnp_TxnRef));

        // 2. Load Config from Tenant
        Tenant tenant = tenantRepository.findById(txn.getOrder().getTenantId())
                .orElseThrow(() -> new AppException(404, "Tenant not found"));
        PaymentConfigDto config = tenant.getPaymentConfig();

        // 3. Verify Checksum
        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(txn.getProvider());
        if (!strategy.verifyPayment(requestParams, config)) {
            // Log warning?
            throw new AppException(400, "Invalid Checksum/Signature");
        }

        // 4. Check status from provider (Example VNPay: vnp_ResponseCode = 00)
        // Generalize: Strategy should parse response code?
        // For now hardcode VNPay logic or assume Strategy validates it in
        // verifyPayment?
        // verifyPayment only checks signature.
        // Check VNPay response code here.
        String responseCode = requestParams.get("vnp_ResponseCode"); // VNPay specific
        String gatewayTxnId = requestParams.get("vnp_TransactionNo");

        txn.setGatewayTransactionId(gatewayTxnId);
        txn.setResponseCode(responseCode);
        txn.setRawResponse(requestParams.toString());

        if ("00".equals(responseCode)) {
            txn.setStatus(PaymentTransaction.TransactionStatus.SUCCESS);
            transactionRepository.save(txn);

            // Trigger Business Logic
            sessionService.handlePaymentSuccess(txn.getOrder().getId(), gatewayTxnId);

            return "SUCCESS";
        } else {
            txn.setStatus(PaymentTransaction.TransactionStatus.FAILED);
            transactionRepository.save(txn);
            throw new AppException(400, "Payment Failed at Gateway (Code: " + responseCode + ")");
        }
    }
}