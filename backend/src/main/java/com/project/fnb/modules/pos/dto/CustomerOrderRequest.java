package com.project.fnb.modules.pos.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Request DTO cho khách hàng đặt món qua QR.
 */
@Data
public class CustomerOrderRequest {
    
    @NotNull(message = "tableId là bắt buộc")
    private Integer tableId;
    
    @NotEmpty(message = "items không được rỗng")
    private List<AddItemRequest> items;
    
    private String customerNote;  // Ghi chú của khách (VD: "Giao nhanh", "Kỵ đậu phộng")
}
