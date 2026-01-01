package com.project.fnb.modules.pos.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Request DTOs cho Session API.
 */
public class SessionRequest {

    /**
     * Request tạo session mới (mở bàn).
     */
    @Data
    public static class OpenSession {
        @NotNull(message = "tableId là bắt buộc")
        private Integer tableId;

        private Integer guestCount;
        private String note;
    }

    /**
     * Request thêm bàn vào session (Attach Table).
     */
    @Data
    public static class AttachTable {
        @NotNull(message = "tableId là bắt buộc")
        private Integer tableId;
    }

    /**
     * Request thêm món vào session.
     */
    @Data
    public static class AddItems {
        private Integer sourceTableId; // optional: bàn nào gọi món

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
     */
    @Data
    public static class PaySession {
        @NotNull(message = "method là bắt buộc")
        private String method; // CASH, VNPAY, MOMO
    }

    /**
     * Request hủy session.
     */
    @Data
    public static class CancelSession {
        private String reason;
    }

    /**
     * Request từ chối session (dùng cho pending session từ khách).
     */
    @Data
    public static class RejectSession {
        private String reason;
    }

    /**
     * Request cập nhật số lượng món (US-14).
     */
    @Data
    public static class UpdateItem {
        @NotNull(message = "quantity là bắt buộc")
        private Integer quantity;
    }
}
