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
 * REST API Controller cho khách hàng đặt món qua QR code (Guest Ordering).
 * 
 * <p>Tất cả endpoints trong controller này là <b>PUBLIC</b> - không yêu cầu authentication.
 * Khách hàng quét mã QR tại bàn và có thể order trực tiếp mà không cần đăng nhập.</p>
 * 
 * <p><b>Guest Ordering Flow:</b></p>
 * <ol>
 *   <li>Khách quét QR code tại bàn</li>
 *   <li>Xem menu và thông tin quán</li>
 *   <li>Chọn món và gửi order (tạo session PENDING)</li>
 *   <li>Nhân viên xác nhận → Session chuyển ACTIVE</li>
 *   <li>Khách có thể tiếp tục order thêm món</li>
 *   <li>Theo dõi trạng thái từng món realtime</li>
 *   <li>Yêu cầu thanh toán khi xong</li>
 * </ol>
 * 
 * <p><b>Security:</b> Không có JWT validation. API chỉ yêu cầu tableId hợp lệ.</p>
 * 
 * <p><b>Base URL:</b> {@code /api/pos/public}</p>
 * 
 * @author FNB Team
 * @version 1.0
 * @see SessionService
 * @see ServingSession
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
     * Lấy menu công khai của quán.
     * 
     * <p>Trả về danh sách danh mục và sản phẩm đang available.
     * Menu được filter theo tenant_id từ context.</p>
     * 
     * <p><b>Response:</b> Danh sách categories với products bên trong, chỉ hiển thị AVAILABLE products.</p>
     * 
     * @return ApiResponse chứa List<PublicMenuDto>
     */
    @GetMapping("/menu")
    public ApiResponse<List<PublicMenuDto>> getMenu() {
        return ApiResponse.success(menuService.getPublicMenu());
    }

    /**
     * Lấy thông tin bàn và quán.
     * 
     * <p>Endpoint này được gọi ngay sau khi khách quét QR code để hiển thị:</p>
     * <ul>
     *   <li>Tên quán và logo</li>
     *   <li>Tên bàn và trạng thái</li>
     *   <li>Có session active không (khách có thể tiếp tục order)</li>
     * </ul>
     * 
     * <p><b>Response Fields:</b></p>
     * <ul>
     *   <li>tableId, tableName, tableStatus</li>
     *   <li>tenantId, tenantName, tenantLogo</li>
     *   <li>hasActiveSession - true nếu có session ACTIVE</li>
     *   <li>sessionId - ID của session hiện tại (nếu có)</li>
     * </ul>
     * 
     * @param tableId ID của bàn (từ QR code)
     * @return ApiResponse chứa Map<String, Object> với thông tin bàn và quán
     * @throws RuntimeException nếu bàn không tồn tại
     */
    @GetMapping("/info/{tableId}")
    public ApiResponse<Map<String, Object>> getTableInfo(@PathVariable String tableId) {
        DiningTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new RuntimeException("Bàn không tồn tại"));

        String tenantId = table.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        // Check xem bàn có session đang active không
        boolean hasActiveSession = table.getCurrentSession() != null
                && table.getCurrentSession().getStatus() == ServingSession.SessionStatus.ACTIVE;

        java.util.HashMap<String, Object> map = new java.util.HashMap<>();
        map.put("tableId", table.getId());
        map.put("tableName", table.getName());
        map.put("tableStatus", table.getStatus().name());
        map.put("tenantId", tenantId);
        map.put("tenantName", tenant.getName());
        map.put("tenantLogo", tenant.getLogoUrl() != null ? tenant.getLogoUrl() : "");
        map.put("hasActiveSession", hasActiveSession);
        map.put("sessionId", table.getCurrentSession() != null ? table.getCurrentSession().getId() : null);

        return ApiResponse.success(map);
    }

    /**
     * Khách hàng đặt món - Tạo hoặc thêm vào session.
     * 
     * <p><b>Luồng xử lý theo trạng thái bàn:</b></p>
     * <ul>
     *   <li><b>Bàn trống (AVAILABLE):</b> Tạo session PENDING + items, chờ nhân viên xác nhận</li>
     *   <li><b>Bàn có session ACTIVE:</b> Thêm món vào session hiện tại ngay</li>
     *   <li><b>Bàn có session PENDING:</b> Báo lỗi - đang chờ xác nhận, không thể order thêm</li>
     *   <li><b>Bàn RESERVED:</b> Báo lỗi - bàn đã được đặt trước</li>
     * </ul>
     * 
     * <p><b>WebSocket:</b> Gửi notification đến nhân viên khi có order mới từ khách.</p>
     * 
     * <p><b>Session Status Flow:</b></p>
     * <pre>
     * Khách đặt món → PENDING → Nhân viên xác nhận → ACTIVE
     * </pre>
     * 
     * @param request chứa tableId, items (productId, quantity), customerNote
     * @return ApiResponse chứa CustomerOrderResponse với sessionId, status, items, totalAmount
     * @throws AppException 404 nếu bàn không tồn tại
     * @throws AppException 400 nếu bàn PENDING/RESERVED hoặc món hết hàng
     */
    @PostMapping("/sessions")
    public ApiResponse<CustomerOrderResponse> createCustomerOrder(
            @RequestBody @Valid CustomerOrderRequest request) {
        CustomerOrderResponse response = sessionService.createCustomerOrder(request);
        return ApiResponse.success(response);
    }

    /**
     * Khách hàng kiểm tra trạng thái order.
     * 
     * <p>Khách sử dụng endpoint này để kiểm tra:</p>
     * <ul>
     *   <li>Trạng thái session (PENDING/ACTIVE/COMPLETED/CANCELLED)</li>
     *   <li>Danh sách món và trạng thái từng món (PENDING/SERVED)</li>
     *   <li>Tổng tiền hiện tại</li>
     *   <li>Lý do từ chối (nếu session bị CANCELLED)</li>
     * </ul>
     * 
     * <p><b>Polling Alternative:</b> Nếu khách mất kết nối WebSocket, có thể polling endpoint này
     * để cập nhật trạng thái.</p>
     * 
     * @param sessionId ID của session cần kiểm tra
     * @return ApiResponse chứa CustomerOrderResponse
     * @throws AppException 404 nếu session không tồn tại
     */
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<CustomerOrderResponse> getCustomerOrderStatus(@PathVariable Long sessionId) {
        CustomerOrderResponse response = sessionService.getCustomerOrderStatus(sessionId);
        return ApiResponse.success(response);
    }

    /**
     * Khách hàng thêm món vào session đang active.
     * 
     * <p>Chỉ sử dụng được khi session đã được nhân viên xác nhận (ACTIVE).
     * Món mới được thêm với trạng thái PENDING.</p>
     * 
     * <p><b>Use Case:</b> Khách order lần đầu → được xác nhận → muốn order thêm món khác.</p>
     * 
     * @param sessionId ID của session đang active
     * @param request chứa danh sách món mới
     * @return ApiResponse chứa CustomerOrderResponse cập nhật
     * @throws AppException 404 nếu session không tồn tại
     * @throws AppException 400 nếu session không ACTIVE hoặc món hết hàng
     */
    @PostMapping("/sessions/{sessionId}/items")
    public ApiResponse<CustomerOrderResponse> addItemsToActiveSession(
            @PathVariable Long sessionId,
            @RequestBody @Valid CustomerOrderRequest request) {
        CustomerOrderResponse response = sessionService.addCustomerItems(sessionId, request);
        return ApiResponse.success(response);
    }

    /**
     * Khách hàng xóa món khỏi session.
     * 
     * <p>Cho phép khách tự xóa món mình đã gọi nhưng chưa được phục vụ.</p>
     * 
     * <p><b>Business Rule:</b> Chỉ xóa được món có status PENDING.
     * Món đã SERVED không thể xóa.</p>
     * 
     * <p><b>Use Case:</b> Khách order nhầm, muốn xóa trước khi bếp chuẩn bị.</p>
     * 
     * @param sessionId ID của session
     * @param itemId ID của order item cần xóa
     * @return ApiResponse với thông báo thành công
     * @throws AppException 404 nếu session/item không tồn tại
     * @throws AppException 409 nếu item không ở trạng thái PENDING
     */
    @DeleteMapping("/sessions/{sessionId}/items/{itemId}")
    public ApiResponse<String> removeCustomerItem(
            @PathVariable Long sessionId,
            @PathVariable Long itemId) {
        sessionService.removeItem(sessionId, itemId);
        return ApiResponse.success("Đã xóa món");
    }

    /**
     * Khách hàng yêu cầu thanh toán (Gọi bill).
     * 
     * <p>Gửi thông báo đến nhân viên để đến bàn xử lý thanh toán.
     * Không thay đổi trạng thái session, chỉ tạo notification.</p>
     * 
     * <p><b>WebSocket:</b> Nhân viên nhận notification với type PAYMENT_REQUEST.</p>
     * 
     * @param sessionId ID của session cần thanh toán
     * @return ApiResponse với thông báo thành công
     * @throws AppException 404 nếu session không tồn tại hoặc đã kết thúc
     */
    @PostMapping("/sessions/{sessionId}/request-payment")
    public ApiResponse<String> requestPayment(@PathVariable Long sessionId) {
        sessionService.requestPayment(sessionId);
        return ApiResponse.success("Đã gửi yêu cầu thanh toán");
    }
}