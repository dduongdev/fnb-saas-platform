package com.project.fnb.modules.pos.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.pos.dto.*;
import com.project.fnb.modules.pos.entity.ServingSession;
import com.project.fnb.modules.pos.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API Controller quản lý Session (phiên phục vụ) trong hệ thống POS.
 * 
 * <p>Controller này cung cấp các endpoint cho nhân viên quản lý session-based operations:</p>
 * <ul>
 *   <li><b>Session Lifecycle:</b> Mở bàn, xác nhận, thanh toán, hủy session</li>
 *   <li><b>Order Management:</b> Thêm, sửa, xóa, serve món</li>
 *   <li><b>Table Operations:</b> Gộp bàn (attach), tách bàn (detach)</li>
 *   <li><b>Pending Orders:</b> Xem và xử lý orders từ khách qua QR code</li>
 * </ul>
 * 
 * <p><b>Session-based Model:</b> Session là khái niệm trung tâm thay thế việc gắn Order trực tiếp vào Table.
 * Điều này cho phép linh hoạt trong việc gộp bàn và quản lý nhóm khách.</p>
 * 
 * <p><b>Authentication:</b> Tất cả endpoints yêu cầu JWT token (nhân viên đã đăng nhập).</p>
 * 
 * <p><b>Base URL:</b> {@code /api/pos/sessions}</p>
 * 
 * @author FNB Team
 * @version 1.0
 * @see SessionService
 * @see ServingSession
 */
@RestController
@RequestMapping("/api/pos/sessions")
@RequiredArgsConstructor
// @PreAuthorize("hasAnyRole('WAITER', 'OWNER', 'ADMIN')")
public class SessionController {

    private final SessionService sessionService;

    /**
     * Lấy danh sách sessions đang chờ xác nhận (PENDING).
     * 
     * <p>Endpoint này trả về tất cả sessions có trạng thái PENDING - các order từ khách quét QR
     * mà nhân viên chưa xác nhận. Kết quả được filter theo tenant hiện tại.</p>
     * 
     * <p><b>Use Case:</b> Nhân viên vào trang "Pending Orders" để xem danh sách order chờ xử lý.</p>
     * 
     * <p><b>WebSocket Alternative:</b> Frontend có thể subscribe topic {@code /topic/tenant/{tenantId}/pending-sessions}
     * để nhận realtime updates thay vì polling endpoint này.</p>
     * 
     * @return ApiResponse chứa danh sách SessionResponse
     */
    @GetMapping("/pending")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.WaiterPermissionValidator.class, com.project.fnb.aspect.OwnerPermissionValidator.class})
    public ApiResponse<List<SessionResponse>> getPendingSessions() {
        List<SessionResponse> pending = sessionService.getPendingSessions();
        return ApiResponse.success(pending);
    }

    /**
     * Lấy danh sách sessions đang hoạt động (ACTIVE).
     * 
     * <p>Endpoint này trả về tất cả sessions đang phục vụ - các bàn có khách đang order và consume.
     * Kết quả được filter theo tenant hiện tại.</p>
     * 
     * <p><b>Use Case:</b> Nhân viên xem tổng quan các bàn đang hoạt động trên dashboard POS.</p>
     * 
     * @return ApiResponse chứa danh sách SessionResponse
     */
    @GetMapping("/active")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<List<SessionResponse>> getActiveSessions() {
        List<SessionResponse> active = sessionService.getActiveSessions();
        return ApiResponse.success(active);
    }

    /**
     * Xác nhận session từ khách (PENDING → ACTIVE).
     * 
     * <p>Sau khi nhân viên xác nhận:</p>
     * <ul>
     *   <li>Session chuyển sang trạng thái ACTIVE</li>
     *   <li>Bàn chuyển sang OCCUPIED</li>
     *   <li>Bếp bắt đầu chuẩn bị món</li>
     *   <li>Khách có thể tiếp tục order thêm món</li>
     * </ul>
     * 
     * <p><b>WebSocket:</b> Gửi notification đến khách và cập nhật dashboard nhân viên.</p>
     * 
     * @param sessionId ID của session cần xác nhận
     * @return ApiResponse chứa SessionResponse đã được cập nhật
     * @throws AppException 404 nếu session không tồn tại
     * @throws AppException 400 nếu session không ở trạng thái PENDING
     */
    @PostMapping("/{sessionId}/confirm")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<SessionResponse> confirmSession(@PathVariable Long sessionId) {
        ServingSession session = sessionService.confirmSession(sessionId);
        return ApiResponse.success(SessionResponse.fromEntity(session));
    }

    /**
     * Từ chối session từ khách (PENDING → CANCELLED).
     * 
     * <p>Sử dụng khi không thể phục vụ do:</p>
     * <ul>
     *   <li>Món hết hàng và không có thay thế</li>
     *   <li>Bàn đã được đặt trước</li>
     *   <li>Gần thời gian đóng cửa</li>
     *   <li>Các lý do đặc biệt khác</li>
     * </ul>
     * 
     * <p><b>Business Impact:</b> Session bị hủy, bàn được giải phóng, khách nhận thông báo với lý do từ chối.</p>
     * 
     * @param sessionId ID của session cần từ chối
     * @param request chứa lý do từ chối (optional, có thể null)
     * @return ApiResponse với thông báo thành công
     * @throws AppException 404 nếu session không tồn tại
     * @throws AppException 400 nếu session không ở trạng thái PENDING
     */
    @PostMapping("/{sessionId}/reject")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<String> rejectSession(
            @PathVariable Long sessionId,
            @RequestBody(required = false) SessionRequest.RejectSession request) {
        if (request == null) {
            request = new SessionRequest.RejectSession();
        }
        sessionService.rejectSession(sessionId, request.getReason());
        return ApiResponse.success("Đã từ chối order");
    }

    /**
     * Tạo session mới - Nhân viên mở bàn thủ công.
     * 
     * <p>Sử dụng khi nhân viên trực tiếp mở bàn cho khách (walk-in, không qua QR).
     * Session được tạo với trạng thái ACTIVE ngay lập tức.</p>
     * 
     * <p><b>Auto-create Order:</b> Tự động tạo 1 order mặc định cho session.</p>
     * 
     * @param request chứa tableId, guestCount (optional), note (optional)
     * @return ApiResponse chứa SessionResponse mới tạo
     * @throws AppException 404 nếu bàn không tồn tại
     */
    @PostMapping
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<SessionResponse> openSession(@RequestBody @Valid SessionRequest.OpenSession request) {
        ServingSession session = sessionService.openTable(request);
        return ApiResponse.success(SessionResponse.fromEntity(session));
    }

    /**
     * Lấy chi tiết session theo ID.
     * 
     * <p>Trả về session đầy đủ thông tin: orders, items, tables, status...</p>
     * 
     * @param sessionId ID của session
     * @return ApiResponse chứa SessionResponse
     * @throws AppException 404 nếu session không tồn tại
     */
    @GetMapping("/{sessionId}")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<SessionResponse> getSession(@PathVariable Long sessionId) {
        ServingSession session = sessionService.getSession(sessionId);
        return ApiResponse.success(SessionResponse.fromEntity(session));
    }

    /**
     * Lấy hoặc tạo session theo table ID.
     * 
     * <p>Endpoint này tự động tạo session ACTIVE nếu bàn chưa có session.
     * Thường được gọi sau khi nhân viên quét QR để xem thông tin bàn.</p>
     * 
     * <p><b>Behavior:</b></p>
     * <ul>
     *   <li>Bàn có session → Trả về session hiện tại</li>
     *   <li>Bàn trống → Tạo session ACTIVE mới</li>
     * </ul>
     * 
     * @param tableId ID của bàn
     * @return ApiResponse chứa SessionResponse
     * @throws AppException 404 nếu bàn không tồn tại
     */
    @GetMapping("/table/{tableId}")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<SessionResponse> getOrCreateByTable(@PathVariable String tableId) {
        ServingSession session = sessionService.getOrCreateByTable(tableId);
        return ApiResponse.success(SessionResponse.fromEntity(session));
    }

    /**
     * Thêm món vào session.
     * 
     * <p>Thêm một hoặc nhiều món vào order chính của session.
     * Giá được snapshot tại thời điểm thêm.</p>
     * 
     * <p><b>WebSocket:</b> Gửi notification ORDER_ITEM_ADDED đến tất cả clients subscribe session này.</p>
     * 
     * @param sessionId ID của session
     * @param request chứa danh sách items (productId, quantity, note)
     * @return ApiResponse với thông báo thành công
     * @throws AppException 404 nếu session/product không tồn tại
     * @throws AppException 400 nếu session không ACTIVE hoặc món hết hàng
     */
    @PostMapping("/{sessionId}/items")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<String> addItems(
            @PathVariable Long sessionId,
            @RequestBody @Valid SessionRequest.AddItems request) {
        sessionService.addItems(sessionId, request);
        return ApiResponse.success("Đã thêm món");
    }

    /**
     * Xóa món khỏi session.
     * 
     * <p><b>Business Rule:</b> Chỉ cho phép xóa món có status = PENDING.
     * Món đã SERVED không thể xóa.</p>
     * 
     * <p><b>WebSocket:</b> Gửi ORDER_ITEM_DELETED event với itemId và tổng tiền mới.</p>
     * 
     * @param sessionId ID của session
     * @param itemId ID của order item cần xóa
     * @return ApiResponse với thông báo thành công
     * @throws AppException 404 nếu session/item không tồn tại
     * @throws AppException 409 nếu item không ở trạng thái PENDING
     */
    @DeleteMapping("/{sessionId}/items/{itemId}")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<String> removeItem(
            @PathVariable Long sessionId,
            @PathVariable Long itemId) {
        sessionService.removeItem(sessionId, itemId);
        return ApiResponse.success("Đã xóa món");
    }

    /**
     * Cập nhật số lượng món trong session.
     * 
     * <p><b>Business Rule:</b> Chỉ cho phép cập nhật món có status = PENDING.
     * Số lượng mới phải >= 1 (nếu muốn xóa, dùng DELETE endpoint).</p>
     * 
     * <p><b>Auto-calculate:</b> Tổng tiền order được tính lại tự động.</p>
     * 
     * @param sessionId ID của session
     * @param itemId ID của order item
     * @param request chứa quantity mới
     * @return ApiResponse với thông báo thành công
     * @throws AppException 400 nếu quantity < 1
     * @throws AppException 409 nếu item không ở trạng thái PENDING
     */
    @PatchMapping("/{sessionId}/items/{itemId}")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<String> updateItem(
            @PathVariable Long sessionId,
            @PathVariable Long itemId,
            @RequestBody SessionRequest.UpdateItem request) {
        sessionService.updateItemQuantity(sessionId, itemId, request.getQuantity());
        return ApiResponse.success("Đã cập nhật số lượng");
    }

    /**
     * Đánh dấu món đã mang ra cho khách (PENDING → SERVED).
     * 
     * <p>Nhân viên phục vụ/bếp gọi endpoint này sau khi mang món ra cho khách.
     * Khách sẽ nhận được notification realtime về món đã được phục vụ.</p>
     * 
     * <p><b>Business Rule:</b> Chỉ món có status = PENDING mới được serve.
     * Món đã SERVED hoặc CANCELLED không thể thay đổi.</p>
     * 
     * @param sessionId ID của session
     * @param itemId ID của order item cần serve
     * @return ApiResponse với thông báo thành công
     * @throws AppException 404 nếu session/item không tồn tại
     * @throws AppException 409 nếu item không ở trạng thái PENDING
     */
    @PostMapping("/{sessionId}/items/{itemId}/serve")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<String> serveItem(
            @PathVariable Long sessionId,
            @PathVariable Long itemId) {
        sessionService.serveItem(sessionId, itemId);
        return ApiResponse.success("Đã đánh dấu món đã mang ra");
    }

    /**
     * Gộp bàn vào session (Attach Table).
     * 
     * <p>Thêm bàn trống vào session hiện tại để phục vụ nhóm khách lớn hoặc gộp chỗ.</p>
     * 
     * <p><b>Business Rules:</b></p>
     * <ul>
     *   <li>Session phải ở trạng thái ACTIVE</li>
     *   <li>Bàn cần attach phải AVAILABLE</li>
     *   <li>Sau khi attach, bàn chuyển sang OCCUPIED</li>
     * </ul>
     * 
     * <p><b>Use Case:</b> Khách ban đầu ngồi bàn 01, sau đó có thêm người đến cần thêm bàn 02.</p>
     * 
     * @param sessionId ID của session
     * @param request chứa tableId cần attach
     * @return ApiResponse với thông báo thành công
     * @throws AppException 404 nếu session/table không tồn tại
     * @throws AppException 400 nếu session không ACTIVE hoặc bàn đang có khách
     */
    @PostMapping("/{sessionId}/tables")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<String> attachTable(
            @PathVariable Long sessionId,
            @RequestBody @Valid SessionRequest.AttachTable request) {
        sessionService.attachTable(sessionId, request.getTableId());
        return ApiResponse.success("Đã thêm bàn vào session");
    }

    /**
     * Tách bàn khỏi session (Detach Table).
     * 
     * <p>Bỏ bàn khỏi session khi một phần khách rời đi.
     * Bàn được tách sẽ chuyển về AVAILABLE và có thể phục vụ khách khác.</p>
     * 
     * <p><b>Business Rules:</b></p>
     * <ul>
     *   <li>Session phải có ít nhất 2 bàn (không thể tách bàn cuối cùng)</li>
     *   <li>Bàn phải thuộc session hiện tại</li>
     *   <li>Session phải ACTIVE hoặc COMPLETED</li>
     * </ul>
     * 
     * <p><b>Use Case:</b> Nhóm 6 người dùng 2 bàn, 3 người rời trước → tách 1 bàn ra.</p>
     * 
     * @param sessionId ID của session
     * @param tableId ID của bàn cần tách
     * @return ApiResponse với thông báo thành công
     * @throws AppException 404 nếu session/table không tồn tại
     * @throws AppException 400 nếu chỉ còn 1 bàn hoặc bàn không thuộc session
     */
    @DeleteMapping("/{sessionId}/tables/{tableId}")
    public ApiResponse<String> detachTable(
            @PathVariable Long sessionId,
            @PathVariable String tableId) {
        sessionService.detachTable(sessionId, tableId);
        return ApiResponse.success("Đã tách bàn khỏi session");
    }

    /**
     * Thanh toán và đóng session.
     * 
     * <p>Xử lý thanh toán và kết thúc phiên phục vụ:</p>
     * <ol>
     *   <li>Đóng tất cả orders (OPEN → COMPLETED)</li>
     *   <li>Giải phóng tất cả bàn (OCCUPIED → AVAILABLE)</li>
     *   <li>Đóng session (ACTIVE → COMPLETED)</li>
     *   <li>Tạo hóa đơn (invoice)</li>
     *   <li>Publish OrderPaidEvent cho báo cáo</li>
     * </ol>
     * 
     * <p><b>Payment Methods:</b> CASH, VNPAY, MOMO, ...</p>
     * 
     * <p><b>WebSocket:</b> Gửi notifications cập nhật trạng thái bàn và session.</p>
     * 
     * @param sessionId ID của session cần thanh toán
     * @param request chứa payment method
     * @return ApiResponse chứa InvoiceDto (hóa đơn)
     * @throws AppException 403 nếu không phải nhân viên
     * @throws AppException 404 nếu session không tồn tại
     */
    @PostMapping("/{sessionId}/pay")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<InvoiceDto> paySession(
            @PathVariable Long sessionId,
            @RequestBody @Valid SessionRequest.PaySession request) {
        InvoiceDto invoice = sessionService.paySession(sessionId, request);
        return ApiResponse.success(invoice);
    }

    /**
     * Hủy session và giải phóng tài nguyên.
     * 
     * <p>Sử dụng khi cần hủy session do khách hủy, lỗi hệ thống, hoặc các lý do khác.</p>
     * 
     * <p><b>Business Impact:</b></p>
     * <ul>
     *   <li>Tất cả orders bị hủy (CANCELLED)</li>
     *   <li>Tất cả bàn được giải phóng (AVAILABLE)</li>
     *   <li>Session bị hủy (CANCELLED)</li>
     *   <li>Lý do hủy được lưu vào note</li>
     * </ul>
     * 
     * @param sessionId ID của session cần hủy
     * @param request chứa lý do hủy (optional, có thể null)
     * @return ApiResponse với thông báo thành công
     * @throws AppException 403 nếu không phải nhân viên
     * @throws AppException 404 nếu session không tồn tại
     */
    @PostMapping("/{sessionId}/cancel")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<String> cancelSession(
            @PathVariable Long sessionId,
            @RequestBody(required = false) SessionRequest.CancelSession request) {
        if (request == null) {
            request = new SessionRequest.CancelSession();
        }
        sessionService.cancelSession(sessionId, request);
        return ApiResponse.success("Đã hủy session");
    }
}
