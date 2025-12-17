package com.project.fnb.modules.payment.strategy;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.modules.payment.enums.PaymentProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentStrategyFactory {

    // Map lưu trữ: Key là Enum, Value là Class xử lý tương ứng
    private final Map<PaymentProvider, PaymentStrategy> strategies = new EnumMap<>(PaymentProvider.class);

    // Spring tự động inject tất cả các Bean implement PaymentStrategy vào List này
    public PaymentStrategyFactory(List<PaymentStrategy> strategyList) {
        for (PaymentStrategy strategy : strategyList) {
            // Đăng ký Strategy vào Map dựa trên Provider của nó
            strategies.put(strategy.getProvider(), strategy);
        }
    }

    public PaymentStrategy getStrategy(PaymentProvider provider) {
        PaymentStrategy strategy = strategies.get(provider);
        
        if (strategy == null) {
            // Trường hợp: Có Enum nhưng chưa viết class xử lý
            throw new AppException(400, "Phương thức thanh toán " + provider + " hiện chưa được hệ thống hỗ trợ (Missing Strategy).");
        }
        
        return strategy;
    }
}