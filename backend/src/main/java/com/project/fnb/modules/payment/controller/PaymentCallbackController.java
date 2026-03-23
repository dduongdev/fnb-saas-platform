package com.project.fnb.modules.payment.controller;

import com.project.fnb.common.utils.VNPayUtils;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.global.dto.PaymentConfigDto;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.payment.entity.PaymentTransaction;
import com.project.fnb.modules.payment.repository.PaymentTransactionRepository;
import com.project.fnb.modules.pos.entity.DiningTable;
import com.project.fnb.modules.pos.entity.Order;
import com.project.fnb.modules.pos.entity.ServingSession;
import com.project.fnb.modules.pos.repository.OrderRepository;
import com.project.fnb.modules.pos.repository.SessionRepository;
import com.project.fnb.modules.pos.repository.TableRepository;
import com.project.fnb.modules.pos.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentCallbackController {

    private final OrderRepository orderRepository;
    private final TenantRepository tenantRepository;
    private final SessionRepository sessionRepository;
    private final TableRepository tableRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final OrderService orderService;

    // --- VNPAY IPN HANDLER (GET) ---
    @GetMapping("/vnpay-ipn")
    @Transactional
    public ResponseEntity<Map<String, String>> vnpayIpn(HttpServletRequest request) {
        try {
            // 1. Lấy toàn bộ tham số từ URL
            Map<String, String> fields = new HashMap<>();
            for (Enumeration<String> params = request.getParameterNames(); params.hasMoreElements();) {
                String fieldName = params.nextElement();
                String fieldValue = request.getParameter(fieldName);
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    fields.put(fieldName, fieldValue);
                }
            }

            // 2. Tìm Order (Global Query - Vì IPN không có Token/TenantContext)
            String vnp_TxnRef = fields.get("vnp_TxnRef");
            long orderId;
            try {
                if (vnp_TxnRef != null && vnp_TxnRef.contains("_")) {
                    String[] parts = vnp_TxnRef.split("_");
                    orderId = Long.parseLong(parts[0]);
                } else {
                    return buildResponse("01", "Invalid Transaction Reference");
                }
            } catch (NumberFormatException e) {
                log.error("Invalid TxnRef format: {}", vnp_TxnRef);
                return buildResponse("01", "Invalid Transaction Reference");
            }
            
            // Hàm findByIdGlobal phải được khai báo trong OrderRepository (Native Query)
            Order order = orderRepository.findByIdGlobal(orderId).orElse(null);
            
            if (order == null) {
                return buildResponse("01", "Order not found");
            }

            // 3. Lấy Config của Tenant để Verify Checksum
            // Vì Order đã có tenantId, ta tìm Tenant để lấy SecretKey
            Tenant tenant = tenantRepository.findById(order.getTenantId()).orElseThrow();
            PaymentConfigDto config = tenant.getPaymentConfig();
            
            if (config == null || config.getVnpay() == null) {
                return buildResponse("02", "Merchant not configured");
            }

            String vnp_SecureHash = fields.get("vnp_SecureHash");
            if (fields.containsKey("vnp_SecureHashType")) fields.remove("vnp_SecureHashType");
            if (fields.containsKey("vnp_SecureHash")) fields.remove("vnp_SecureHash");
            
            // Tái tạo checksum để so sánh
            String signValue = VNPayUtils.hashAllFields(fields, config.getVnpay().getHashSecret());
            
            if (!signValue.equals(vnp_SecureHash)) {
                return buildResponse("97", "Invalid Checksum");
            }

            // 4. Kiểm tra trạng thái đơn hàng (Idempotency)
            if (order.getStatus() == Order.OrderStatus.COMPLETED) {
                return buildResponse("02", "Order already confirmed");
            }

            PaymentTransaction trans = transactionRepository.findByTransactionRef(vnp_TxnRef)
                .orElse(null);

            if (trans != null) {
                // Update thông tin từ cổng thanh toán trả về
                trans.setGatewayTransactionId(fields.get("vnp_TransactionNo"));
                trans.setResponseCode(fields.get("vnp_ResponseCode"));
            }

            // 5. Xử lý kết quả giao dịch
            TenantContext.setTenantId(tenant.getId());
            try {
                if ("00".equals(fields.get("vnp_ResponseCode"))) {
                    // SUCCESS
                    if (trans != null) {
                        trans.setStatus(PaymentTransaction.TransactionStatus.SUCCESS);
                        // Cập nhật các trường khác từ VNPay trả về nếu cần
                        transactionRepository.save(trans);
                    }

                    // Update Order
                    order.setStatus(Order.OrderStatus.COMPLETED);
                    order.setPaymentMethod("VNPAY");
                    order.setCompletedAt(LocalDateTime.now());
                    orderRepository.save(order);

                    // Giải phóng session & tables
                    ServingSession session = order.getSession();
                    if (session != null) {
                        for (DiningTable table : session.getTables()) {
                            table.setCurrentSession(null);
                            table.setStatus(DiningTable.Status.AVAILABLE);
                        }
                        tableRepository.saveAll(session.getTables());
                        session.setStatus(ServingSession.SessionStatus.COMPLETED);
                        session.setEndedAt(LocalDateTime.now());
                        sessionRepository.save(session);
                    }
                    
                    orderService.notifyPaymentSuccess(order);
                    
                    log.info("✅ VNPay Success: Order #{}", orderId);
                } else {
                    // FAILED
                    if (trans != null) {
                        trans.setStatus(PaymentTransaction.TransactionStatus.FAILED);
                        transactionRepository.save(trans);
                    }

                    log.info("❌ VNPay Failed: Order #{}", orderId);
                }
            } finally {
                TenantContext.clear();
            }

            return buildResponse("00", "Confirm Success");

        } catch (Exception e) {
            log.error("IPN Exception", e);
            return buildResponse("99", "Unknown error");
        }
    }

    // --- Helper trả về JSON chuẩn IPN VNPay ---
    private ResponseEntity<Map<String, String>> buildResponse(String code, String message) {
        Map<String, String> response = new HashMap<>();
        response.put("RspCode", code);
        response.put("Message", message);
        return ResponseEntity.ok(response);
    }

    
}