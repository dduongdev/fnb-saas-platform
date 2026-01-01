package com.project.fnb.modules.pos.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.menu.service.MenuService;
import com.project.fnb.modules.pos.dto.CustomerOrderRequest;
import com.project.fnb.modules.pos.dto.CustomerOrderResponse;
import com.project.fnb.modules.pos.dto.PublicMenuDto;
import com.project.fnb.modules.pos.entity.DiningTable;
import com.project.fnb.modules.pos.entity.ServingSession;
import com.project.fnb.modules.pos.repository.TableRepository;
import com.project.fnb.modules.pos.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller cho khách hàng đặt món qua QR.
 * Tất cả API trong đây đều public (không cần auth).
 */
@RestController
@RequestMapping("/api/pos/public")
@RequiredArgsConstructor
public class CustomerController {

    private final MenuService menuService;
    private final TableRepository tableRepository;
    private final TenantRepository tenantRepository;
    private final SessionService sessionService;

    /**
     * Lấy menu công khai.
     */
    @GetMapping("/menu")
    public ApiResponse<List<PublicMenuDto>> getMenu() {
        return ApiResponse.success(menuService.getPublicMenu());
    }

    /**
     * Lấy thông tin bàn và quán.
     */
    @GetMapping("/info/{tableId}")
    public ApiResponse<Map<String, Object>> getTableInfo(@PathVariable Integer tableId) {
        DiningTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new RuntimeException("Bàn không tồn tại"));

        String tenantId = table.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        // Check xem bàn có session đang active không
        boolean hasActiveSession = table.getCurrentSession() != null 
                && table.getCurrentSession().getStatus() == ServingSession.SessionStatus.ACTIVE;

        return ApiResponse.success(Map.of(
            "tableId", table.getId(),
            "tableName", table.getName(),
            "tableStatus", table.getStatus().name(),
            "tenantId", tenantId,
            "tenantName", tenant.getName(),
            "tenantLogo", tenant.getLogoUrl() != null ? tenant.getLogoUrl() : "",
            "hasActiveSession", hasActiveSession,
            "sessionId", table.getCurrentSession() != null ? table.getCurrentSession().getId() : null
        ));
    }

    /**
     * Khách đặt món - tạo pending session.
     * 
     * <p>Luồng:</p>
     * <ul>
     *   <li>Nếu bàn trống (AVAILABLE) → Tạo session PENDING + items</li>
     *   <li>Nếu bàn đã có session ACTIVE → Thêm món vào session hiện tại</li>
     *   <li>Nếu bàn đã có session PENDING → Báo lỗi (đang chờ xác nhận)</li>
     *   <li>Nếu bàn RESERVED → Báo lỗi</li>
     * </ul>
     */
    @PostMapping("/sessions")
    public ApiResponse<CustomerOrderResponse> createCustomerOrder(
            @RequestBody @Valid CustomerOrderRequest request) {
        CustomerOrderResponse response = sessionService.createCustomerOrder(request);
        return ApiResponse.success(response);
    }

    /**
     * Khách kiểm tra trạng thái order của mình.
     */
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<CustomerOrderResponse> getCustomerOrderStatus(@PathVariable Long sessionId) {
        CustomerOrderResponse response = sessionService.getCustomerOrderStatus(sessionId);
        return ApiResponse.success(response);
    }

    /**
     * Khách thêm món vào session đang active.
     * Chỉ dùng được khi session đã ACTIVE.
     */
    @PostMapping("/sessions/{sessionId}/items")
    public ApiResponse<CustomerOrderResponse> addItemsToActiveSession(
            @PathVariable Long sessionId,
            @RequestBody @Valid CustomerOrderRequest request) {
        CustomerOrderResponse response = sessionService.addCustomerItems(sessionId, request);
        return ApiResponse.success(response);
    }
}