package com.project.fnb.modules.pos.dto;
import com.project.fnb.modules.pos.entity.DiningTable;
import lombok.Builder;
import lombok.Data;

/**
 * Response DTO cho thông tin bàn ăn.
 * 
 * <p><b>Fields:</b></p>
 * <ul>
 *   <li>id: ID bàn</li>
 *   <li>name: Tên bàn hiển thị (VD: "Bàn 01", "VIP 1")</li>
 *   <li>status: Trạng thái bàn (AVAILABLE, OCCUPIED, RESERVED)</li>
 *   <li>qrCodeUrl: URL ảnh QR code để khách quét order</li>
 *   <li>sessionId: ID của session hiện tại nếu bàn đang có khách (null nếu trống)</li>
 * </ul>
 * 
 * <p><b>Use Cases:</b></p>
 * <ul>
 *   <li>Hiển thị floor map trong POS UI</li>
 *   <li>WebSocket updates qua topic {@code /topic/tenant/{tenantId}/tables}</li>
 *   <li>Response của API {@code GET /api/pos/tables}</li>
 * </ul>
 * 
 * @see DiningTable
 * @see com.project.fnb.modules.pos.service.TableService
 */
@Data
@Builder
public class TableDto {
    private Integer id;
    private String name;
    private DiningTable.Status status;
    private String qrCodeUrl;
    private Long sessionId;
}