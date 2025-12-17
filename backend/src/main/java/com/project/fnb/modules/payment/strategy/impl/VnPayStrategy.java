package com.project.fnb.modules.payment.strategy.impl;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.common.utils.VNPayUtils;
import com.project.fnb.modules.global.dto.PaymentConfigDto;
import com.project.fnb.modules.payment.enums.PaymentProvider;
import com.project.fnb.modules.payment.strategy.PaymentStrategy;
import com.project.fnb.modules.pos.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class VnPayStrategy implements PaymentStrategy {

    // Config hệ thống (Lấy từ .env / application.yml)
    @Value("${app.payment.vnpay.api-url}")
    private String vnpApiUrl;

    @Value("${app.payment.vnpay.return-url}")
    private String vnpReturnUrl;

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.VNPAY;
    }

    @Override
    public String createPaymentUrl(Order order, PaymentConfigDto config, String ipAddress, String txnRef) {
        // 1. Trích xuất và Validate Config của VNPay
        PaymentConfigDto.VNPayConfig vnpConfig = config.getVnpay();

        if (vnpConfig == null || !Boolean.TRUE.equals(vnpConfig.getEnabled())) {
            throw new AppException(400, "Cổng thanh toán VNPay chưa được kích hoạt tại quán này.");
        }

        if (vnpConfig.getTmnCode() == null || vnpConfig.getHashSecret() == null) {
            throw new AppException(400, "Cấu hình VNPay của quán bị thiếu thông tin (TmnCode/Secret).");
        }

        // 2. Chuẩn bị tham số chuẩn VNPay
        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String vnp_TxnRef = txnRef;
        String vnp_OrderInfo = "Thanh toan don hang #" + order.getId();
        String vnp_OrderType = "other";
        String vnp_Locale = "vn";
        String vnp_CurrCode = "VND";

        // Số tiền: VNPay yêu cầu nhân 100 (đơn vị đồng)
        long amount = order.getTotalAmount().multiply(new BigDecimal(100)).longValue();

        // Ngày giờ
        ZoneId vietnamZone = ZoneId.of("Asia/Ho_Chi_Minh");
        ZonedDateTime now = ZonedDateTime.now(vietnamZone);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String vnp_CreateDate = now.format(formatter);
        
        String vnp_ExpireDate = now.plusMinutes(15).format(formatter);

        // 3. Đưa vào Map để sắp xếp
        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnpConfig.getTmnCode()); // Lấy từ DB Tenant
        vnp_Params.put("vnp_Amount", String.valueOf(amount));
        vnp_Params.put("vnp_CurrCode", vnp_CurrCode);
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", vnp_OrderInfo);
        vnp_Params.put("vnp_OrderType", vnp_OrderType);
        vnp_Params.put("vnp_Locale", vnp_Locale);
        vnp_Params.put("vnp_ReturnUrl", vnpReturnUrl); // Lấy từ System Config
        vnp_Params.put("vnp_IpAddr", ipAddress);
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        // 4. Build Query String & Checksum
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        
        try {
            while (itr.hasNext()) {
                String fieldName = itr.next();
                String fieldValue = vnp_Params.get(fieldName);
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    // Build hash data
                    hashData.append(fieldName);
                    hashData.append('=');
                    hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    
                    // Build query
                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                    query.append('=');
                    query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    
                    if (itr.hasNext()) {
                        query.append('&');
                        hashData.append('&');
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error encoding URL", e);
        }

        String queryUrl = query.toString();
        // Tạo chữ ký bằng SecretKey của Tenant
        String vnp_SecureHash = VNPayUtils.hmacSHA512(vnpConfig.getHashSecret(), hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
        
        return vnpApiUrl + "?" + queryUrl;
    }
}