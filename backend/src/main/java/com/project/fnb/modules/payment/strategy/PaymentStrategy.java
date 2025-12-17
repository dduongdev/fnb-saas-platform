package com.project.fnb.modules.payment.strategy;

import com.project.fnb.modules.global.dto.PaymentConfigDto;
import com.project.fnb.modules.payment.enums.PaymentProvider;
import com.project.fnb.modules.pos.entity.Order;

public interface PaymentStrategy {
    
    /**
     * Xác định Strategy này dùng cho Provider nào (VNPAY, MOMO...)
     */
    PaymentProvider getProvider();

    /**
     * Tạo URL thanh toán để redirect khách hàng
     * @param order Thông tin đơn hàng (ID, số tiền...)
     * @param config Cấu hình tổng của Tenant (Strategy sẽ tự lấy phần config con tương ứng)
     * @param ipAddress IP của khách hàng (Cần thiết cho VNPay/Momo để chống fraud)
     * @return Payment URL
     */
    String createPaymentUrl(Order order, PaymentConfigDto config, String ipAddress, String txnRef);
}