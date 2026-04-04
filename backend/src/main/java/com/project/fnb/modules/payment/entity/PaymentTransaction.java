package com.project.fnb.modules.payment.entity;

import com.project.fnb.common.BaseEntity;
import com.project.fnb.modules.payment.enums.PaymentProvider;
import com.project.fnb.modules.pos.entity.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

@Entity
@Table(
    name = "payment_transactions",
    indexes = {
        @Index(name = "idx_payment_status_date", columnList = "status, created_at"),
        @Index(name = "idx_payment_order_id", columnList = "order_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class PaymentTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider; // VNPAY, MOMO

    // Mã tham chiếu gửi sang cổng thanh toán (Thường là OrderId hoặc OrderId_Timestamp)
    @Column(name = "transaction_ref", nullable = false)
    private String transactionRef;

    // Mã giao dịch từ cổng thanh toán trả về (vnp_TransactionNo) - Lưu khi IPN gọi về
    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "response_code")
    private String responseCode; // Mã lỗi từ cổng TT (VD: 00, 01, 99)

    @Column(columnDefinition = "TEXT")
    private String rawResponse; // Lưu JSON/String response để debug

    public enum TransactionStatus {
        PENDING, // Đang chờ thanh toán
        SUCCESS, // Thành công
        FAILED   // Thất bại
    }
}