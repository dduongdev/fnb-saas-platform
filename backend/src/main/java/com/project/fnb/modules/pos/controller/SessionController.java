package com.project.fnb.modules.pos.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.pos.dto.*;
import com.project.fnb.modules.pos.entity.ServingSession;
import com.project.fnb.modules.pos.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller cho Session API.
 * 
 * <p>
 * Session-based design thay thế việc gắn Order trực tiếp vào Table.
 * Tất cả nghiệp vụ POS đi qua Session.
 * </p>
 */
@RestController
@RequestMapping("/api/pos/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    // ==================== PENDING SESSION MANAGEMENT ====================

    /**
     * Lấy danh sách session đang chờ xác nhận.
     * Dùng cho nhân viên xem các order từ khách quét QR.
     */
    @GetMapping("/pending")
    public ApiResponse<List<SessionResponse>> getPendingSessions() {
        List<SessionResponse> pending = sessionService.getPendingSessions();
        return ApiResponse.success(pending);
    }

    /**
     * Lấy danh sách session đang hoạt động (ACTIVE).
     */
    @GetMapping("/active")
    public ApiResponse<List<SessionResponse>> getActiveSessions() {
        List<SessionResponse> active = sessionService.getActiveSessions();
        return ApiResponse.success(active);
    }

    /**
     * Xác nhận session (chuyển từ PENDING → ACTIVE).
     * Nhân viên xác nhận order từ khách.
     */
    @PostMapping("/{sessionId}/confirm")
    public ApiResponse<SessionResponse> confirmSession(@PathVariable Long sessionId) {
        ServingSession session = sessionService.confirmSession(sessionId);
        return ApiResponse.success(SessionResponse.fromEntity(session));
    }

    /**
     * Từ chối session (chuyển từ PENDING → CANCELLED).
     * Nhân viên từ chối order từ khách.
     */
    @PostMapping("/{sessionId}/reject")
    public ApiResponse<String> rejectSession(
            @PathVariable Long sessionId,
            @RequestBody(required = false) SessionRequest.RejectSession request) {
        if (request == null) {
            request = new SessionRequest.RejectSession();
        }
        sessionService.rejectSession(sessionId, request.getReason());
        return ApiResponse.success("Đã từ chối order");
    }

    // ==================== EXISTING APIS ====================

    /**
     * Tạo session mới (mở bàn).
     */
    @PostMapping
    public ApiResponse<SessionResponse> openSession(@RequestBody @Valid SessionRequest.OpenSession request) {
        ServingSession session = sessionService.openTable(request);
        return ApiResponse.success(SessionResponse.fromEntity(session));
    }

    /**
     * Lấy session theo ID.
     */
    @GetMapping("/{sessionId}")
    public ApiResponse<SessionResponse> getSession(@PathVariable Long sessionId) {
        ServingSession session = sessionService.getSession(sessionId);
        return ApiResponse.success(SessionResponse.fromEntity(session));
    }

    /**
     * Lấy session theo tableId (dùng khi quét QR).
     * Tự động tạo session mới nếu chưa có.
     */
    @GetMapping("/table/{tableId}")
    public ApiResponse<SessionResponse> getOrCreateByTable(@PathVariable Integer tableId) {
        ServingSession session = sessionService.getOrCreateByTable(tableId);
        return ApiResponse.success(SessionResponse.fromEntity(session));
    }

    /**
     * Thêm món vào session.
     */
    @PostMapping("/{sessionId}/items")
    public ApiResponse<String> addItems(
            @PathVariable Long sessionId,
            @RequestBody @Valid SessionRequest.AddItems request) {
        sessionService.addItems(sessionId, request);
        return ApiResponse.success("Đã thêm món");
    }

    /**
     * Xóa món khỏi session.
     */
    @DeleteMapping("/{sessionId}/items/{itemId}")
    public ApiResponse<String> removeItem(
            @PathVariable Long sessionId,
            @PathVariable Long itemId) {
        sessionService.removeItem(sessionId, itemId);
        return ApiResponse.success("Đã xóa món");
    }

    /**
     * Cập nhật số lượng món trong session (US-14).
     */
    @PatchMapping("/{sessionId}/items/{itemId}")
    public ApiResponse<String> updateItem(
            @PathVariable Long sessionId,
            @PathVariable Long itemId,
            @RequestBody SessionRequest.UpdateItem request) {
        sessionService.updateItemQuantity(sessionId, itemId, request.getQuantity());
        return ApiResponse.success("Đã cập nhật số lượng");
    }

    /**
     * Đánh dấu món đã mang ra (SERVE).
     * Chỉ cho phép với món có status PENDING.
     * Trả về 409 CONFLICT nếu món không ở trạng thái PENDING.
     */
    @PostMapping("/{sessionId}/items/{itemId}/serve")
    public ApiResponse<String> serveItem(
            @PathVariable Long sessionId,
            @PathVariable Long itemId) {
        sessionService.serveItem(sessionId, itemId);
        return ApiResponse.success("Đã đánh dấu món đã mang ra");
    }

    // ==================== 3. QUẢN LÝ BÀN TRONG SESSION (ATTACH / DETACH)
    // ====================

    /**
     * Attach Table: Thêm bàn vào session.
     */
    @PostMapping("/{sessionId}/tables")
    public ApiResponse<String> attachTable(
            @PathVariable Long sessionId,
            @RequestBody @Valid SessionRequest.AttachTable request) {
        sessionService.attachTable(sessionId, request.getTableId());
        return ApiResponse.success("Đã thêm bàn vào session");
    }

    /**
     * Detach Table: Bỏ bàn khỏi session.
     */
    @DeleteMapping("/{sessionId}/tables/{tableId}")
    public ApiResponse<String> detachTable(
            @PathVariable Long sessionId,
            @PathVariable Integer tableId) {
        sessionService.detachTable(sessionId, tableId);
        return ApiResponse.success("Đã tách bàn khỏi session");
    }

    // ==================== [REMOVED] LEGACY OPERATIONS ====================
    // Các endpoints sau đã được LOẠI BỎ theo refactor Session-based:
    //
    // POST /{sessionId}/merge -> Thay bằng: POST /{sessionId}/tables
    // POST /{sessionId}/split -> KHÔNG HỖ TRỢ split bill
    // -> Để tách bàn: DELETE /{sessionId}/tables/{tableId}
    // POST /{sessionId}/transfer -> Thay bằng: POST /{sessionId}/tables (attach
    // new)
    // + DELETE /{sessionId}/tables/{id} (detach old)
    //
    // Nếu cần backward compatibility, frontend phải migrate sang API mới.

    /**
     * Thanh toán và đóng session.
     */
    @PostMapping("/{sessionId}/pay")
    public ApiResponse<InvoiceDto> paySession(
            @PathVariable Long sessionId,
            @RequestBody @Valid SessionRequest.PaySession request) {
        InvoiceDto invoice = sessionService.paySession(sessionId, request);
        return ApiResponse.success(invoice);
    }

    /**
     * Hủy session.
     */
    @PostMapping("/{sessionId}/cancel")
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
