package com.project.fnb.modules.pos.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.pos.dto.AddItemRequest;
import com.project.fnb.modules.pos.dto.InvoiceDto;
import com.project.fnb.modules.pos.dto.OrderResponse;
import com.project.fnb.modules.pos.entity.Order;
import com.project.fnb.modules.pos.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pos/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/table/{tableId}")
    public ApiResponse<OrderResponse> getOrderSession(@PathVariable Integer tableId) {
        Order order = orderService.createOrGetSession(tableId);
        return ApiResponse.success(OrderResponse.fromEntity(order));
    }

    // API Thêm món
    @PostMapping("/table/{tableId}/items")
    public ApiResponse<String> addItems(
            @PathVariable Integer tableId,
            @RequestBody List<AddItemRequest> items) {
        orderService.addItems(tableId, items);
        return ApiResponse.success("Đã thêm món vào order");
    }

    // API Xóa món (khi chưa gửi bếp)
    @DeleteMapping("/items/{itemId}")
    public ApiResponse<String> removeItem(@PathVariable Long itemId) {
        orderService.removeItem(itemId);
        return ApiResponse.success("Đã xóa món");
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<String> cancelOrder(@PathVariable Long orderId, 
                                           @RequestParam(required = false) String reason) {
        orderService.cancelOrder(orderId, reason);
        return ApiResponse.success("Đã hủy đơn hàng");
    }

    @PostMapping("/{orderId}/pay/cash")
    public ApiResponse<InvoiceDto> payCash(@PathVariable Long orderId) {
        InvoiceDto invoice = orderService.payCash(orderId);
        return ApiResponse.success(invoice); // Frontend nhận cục này rồi render ra HTML để in
    }
}