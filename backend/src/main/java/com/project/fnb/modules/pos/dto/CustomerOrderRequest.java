package com.project.fnb.modules.pos.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Request DTO cho khách hàng đặt món qua QR code.
 * 
 * <p><b>Public API:</b> Endpoint này KHÔNG CẦN AUTHENTICATION, khách quét QR bàn
 * và order trực tiếp mà không cần đăng nhập.</p>
 * 
 * <p><b>Business Flow:</b></p>
 * <ol>
 *   <li>Khách quét QR code trên bàn</li>
 *   <li>Xem menu và chọn món</li>
 *   <li>Submit CustomerOrderRequest với tableId và items</li>
 *   <li>Hệ thống tạo Session với status PENDING</li>
 *   <li>Staff có thể Confirm (ACTIVE) hoặc Reject</li>
 *   <li>Khách nhận WebSocket notification về trạng thái order</li>
 * </ol>
 * 
 * <p><b>Validation:</b></p>
 * <ul>
 *   <li>tableId: Bắt buộc, phải là bàn AVAILABLE</li>
 *   <li>items: Bắt buộc, không được rỗng</li>
 *   <li>customerNote: Tuỳ chọn, ghi chú của khách (VD: "Giao nhanh", "Kỵ đậu phộng")</li>
 * </ul>
 * 
 * @see com.project.fnb.modules.pos.controller.CustomerController
 * @see AddItemRequest
 */
@Data
public class CustomerOrderRequest {
    
    @NotNull(message = "tableId là bắt buộc")
    private String tableId;
    
    @NotEmpty(message = "items không được rỗng")
    private List<AddItemRequest> items;
    
    private String customerNote;  // Ghi chú của khách (VD: "Giao nhanh", "Kỵ đậu phộng")
}
