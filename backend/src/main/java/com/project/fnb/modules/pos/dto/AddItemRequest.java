package com.project.fnb.modules.pos.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO để thêm món vào order.
 * 
 * <p><b>Validation Rules:</b></p>
 * <ul>
 *   <li>productId: Bắt buộc, phải tồn tại trong menu và có status AVAILABLE</li>
 *   <li>quantity: Phải >= 1</li>
 *   <li>note: Tuỳ chọn, dùng để ghi chú yêu cầu đặc biệt (VD: "Không hành", "It đá")</li>
 * </ul>
 * 
 * <p><b>Use Cases:</b></p>
 * <ul>
 *   <li>Được sử dụng trong {@link SessionRequest.AddItems}</li>
 *   <li>Được sử dụng trong {@link CustomerOrderRequest} (khách order qua QR)</li>
 * </ul>
 * 
 * @see SessionRequest.AddItems
 * @see CustomerOrderRequest
 */
@Data
public class AddItemRequest {
    @NotNull private Long productId;
    @Min(1) private Integer quantity;
    private String note;
}