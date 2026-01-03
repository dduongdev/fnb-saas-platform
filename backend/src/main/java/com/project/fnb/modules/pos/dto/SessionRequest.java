package com.project.fnb.modules.pos.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Nhóm các Request DTOs cho Session API.
 * 
 * <p><b>Architecture:</b> Theo Session-based model, mỗi request tương ứng với một
 * action cụ thể trong session lifecycle.</p>
 * 
 * <p><b>Available Requests:</b></p>
 * <ul>
 *   <li>{@link OpenSession} - Mở session mới (check-in khách)</li>
 *   <li>{@link AttachTable} - Thêm bàn vào session hiện tại (merge tables)</li>
 *   <li>{@link AddItems} - Thêm món vào session</li>
 *   <li>{@link PaySession} - Thanh toán session</li>
 *   <li>{@link CancelSession} - Hủy session</li>
 *   <li>{@link RejectSession} - Từ chối session PENDING từ khách</li>
 *   <li>{@link UpdateItem} - Cập nhật số lượng món</li>
 * </ul>
 * 
 * <p><b>Removed DTOs (Legacy):</b></p>
 * <ul>
 *   <li>MergeTables → Sử dụng AttachTable</li>
 *   <li>SplitSession → KHÔNG HỖ TRỢ split bill (business constraint)</li>
 *   <li>TransferSession → Sử dụng sequence của Attach + Detach</li>
 * </ul>
 * 
 * @see com.project.fnb.modules.pos.controller.SessionController
 * @see com.project.fnb.modules.pos.service.SessionService
 */
public class SessionRequest {

    /**
     * Request để mở session mới (check-in khách).
     * 
     * <p><b>Business Flow:</b></p>
     * <ol>
     *   <li>Staff chọn bàn AVAILABLE</li>
     *   <li>Nhập số khách và ghi chú (optional)</li>
     *   <li>Hệ thống tạo Session với status ACTIVE</li>
     *   <li>Bàn chuyển sang OCCUPIED</li>
     * </ol>
     */
    @Data
    public static class OpenSession {
        @NotNull(message = "tableId là bắt buộc")
        private String tableId;

        private Integer guestCount;
        private String note;
    }

    /**
     * Request để thêm bàn vào session hiện tại (Merge Tables).
     * 
     * <p><b>Use Case:</b> Khi nhóm khách lớn cần ghép nhiều bàn lại.</p>
     * 
     * <p><b>Business Rules:</b></p>
     * <ul>
     *   <li>Bàn được thêm phải ở trạng thái AVAILABLE</li>
     *   <li>Session phải ở trạng thái ACTIVE</li>
     *   <li>Tất cả bàn trong session sẽ share chung orders và totalAmount</li>
     * </ul>
     * 
     * <p><b>Example:</b> Session ban đầu có Bàn 1, attach thêm Bàn 2 → Session có [Bàn 1, Bàn 2]</p>
     */
    @Data
    public static class AttachTable {
        @NotNull(message = "tableId là bắt buộc")
        private String tableId;
    }

    /**
     * Request để thêm món vào session.
     * 
     * <p><b>Behavior:</b></p>
     * <ul>
     *   <li>Nếu session chỉ có 1 bàn: Tạo Order gắn với bàn đó</li>
     *   <li>Nếu session có nhiều bàn (merged): Dùng {@code sourceTableId} để xác định bàn nào gọi món</li>
     * </ul>
     * 
     * <p><b>Price Snapshot:</b> Giá món được snapshot tại thời điểm thêm vào order,
     * không bị ảnh hưởng bởi thay đổi giá menu sau này.</p>
     * 
     * <p><b>WebSocket:</b> Sau khi thêm thành công, push event qua topic
     * {@code /topic/tenant/{tenantId}/table/{tableId}} cho tất cả tables trong session.</p>
     */
    @Data
    public static class AddItems {
        private String sourceTableId; // optional: bàn nào gọi món

        @NotNull(message = "items là bắt buộc")
        private List<AddItemRequest> items;
    }

    // ==================== [REMOVED] LEGACY REQUEST DTOs ====================
    // Các DTOs sau đã được LOẠI BỎ theo refactor Session-based:
    // - MergeTables -> Use AttachTable
    // - SplitSession -> KHÔNG HỖ TRỢ split bill
    // - TransferSession -> Use sequence of Attach + Detach

    /**
     * Request thanh toán session.
     * 
     * <p><b>Payment Methods:</b></p>
     * <ul>
     *   <li>CASH - Tiền mặt (instant complete)</li>
     *   <li>VNPAY - VNPay gateway (redirect đến VNPay, chờ callback)</li>
     *   <li>MOMO - MoMo gateway (redirect đến MoMo, chờ callback)</li>
     * </ul>
     * 
     * <p><b>Business Flow:</b></p>
     * <ol>
     *   <li>Validate session ACTIVE và có ít nhất 1 món</li>
     *   <li>Tạo payment transaction</li>
     *   <li>Nếu CASH: Complete ngay, session → COMPLETED, bàn → AVAILABLE</li>
     *   <li>Nếu VNPay/MoMo: Trả URL redirect, chờ callback</li>
     * </ol>
     */
    @Data
    public static class PaySession {
        @NotNull(message = "method là bắt buộc")
        private String method; // CASH, VNPAY, MOMO
    }

    /**
     * Request hủy session.
     * 
     * <p><b>Business Rules:</b></p>
     * <ul>
     *   <li>Chỉ hủy được session ACTIVE hoặc PENDING</li>
     *   <li>Session → CANCELLED</li>
     *   <li>Tất cả bàn trong session → AVAILABLE</li>
     *   <li>Lý do hủy được lưu lại để audit</li>
     * </ul>
     * 
     * <p><b>Use Cases:</b> Khách hủy order, nhân viên nhập nhầm, khách không trả tiền bỏ đi...</p>
     */
    @Data
    public static class CancelSession {
        private String reason;
    }

    /**
     * Request từ chối session PENDING từ khách.
     * 
     * <p><b>Context:</b> Khi khách order qua QR, session được tạo với status PENDING.
     * Staff có thể Confirm (chuyển ACTIVE) hoặc Reject.</p>
     * 
     * <p><b>Business Rules:</b></p>
     * <ul>
     *   <li>Chỉ reject được session PENDING</li>
     *   <li>Session → CANCELLED</li>
     *   <li>Bàn → AVAILABLE</li>
     *   <li>WebSocket thông báo cho khách qua topic table</li>
     * </ul>
     * 
     * <p><b>Use Cases:</b> Khách order nhầm bàn, order ngoài giờ, order món hết...</p>
     */
    @Data
    public static class RejectSession {
        private String reason;
    }

    /**
     * Request cập nhật số lượng món.
     * 
     * <p><b>Business Rules:</b></p>
     * <ul>
     *   <li>Chỉ cập nhật được món PENDING</li>
     *   <li>Quantity phải >= 1</li>
     *   <li>Tự động recalculate totalAmount của Order và Session</li>
     * </ul>
     * 
     * <p><b>WebSocket:</b> Push event ORDER_ITEM_UPDATED qua topic table.</p>
     * 
     * <p><b>Use Cases:</b> Khách đổi ý tăng/giảm số lượng món vừa gọi.</p>
     */
    @Data
    public static class UpdateItem {
        @NotNull(message = "quantity là bắt buộc")
        private Integer quantity;
    }
}
