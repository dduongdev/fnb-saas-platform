package com.project.fnb.modules.pos.entity;

import com.project.fnb.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Entity đại diện cho bàn ăn trong nhà hàng.
 * 
 * <p><b>Session-based Model:</b> Bàn thuộc về Session thay vì có Order riêng.
 * Điều này cho phép linh hoạt trong việc gộp bàn và quản lý nhóm khách.</p>
 * 
 * <p><b>QR Code Integration:</b> Mỗi bàn có mã QR riêng để khách quét và order.
 * QR code chứa URL tới trang customer menu với tableId (UUID).</p>
 * 
 * <p><b>Security:</b> Sử dụng UUID thay vì auto-increment ID để tránh 
 * enumeration attack qua QR code URL.</p>
 * 
 * <p><b>Trạng thái bàn:</b></p>
 * <ul>
 *   <li><b>AVAILABLE:</b> Trống, sẵn sàng phục vụ</li>
 *   <li><b>OCCUPIED:</b> Đang có khách (thuộc 1 session)</li>
 *   <li><b>RESERVED:</b> Đã đặt trước</li>
 *   <li><b>SERVING:</b> Legacy status, tương đương OCCUPIED</li>
 * </ul>
 * 
 * <p><b>Business Rules:</b></p>
 * <ul>
 *   <li>Bàn chỉ thuộc tối đa 1 Session ACTIVE</li>
 *   <li>currentSession = null khi bàn AVAILABLE</li>
 *   <li>Không thể attach bàn OCCUPIED vào session khác</li>
 *   <li>QR code được generate tự động khi tạo bàn</li>
 * </ul>
 * 
 * @author FNB Team
 * @version 1.1
 * @see ServingSession
 */
@Entity
@Table(name = "dining_tables")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE dining_tables SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class DiningTable extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private Status status = Status.AVAILABLE;

    @Column(name = "qr_code_url", length = 500)
    private String qrCodeUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_session_id")
    private ServingSession currentSession;

    /**
     * Enum trạng thái của bàn ăn.
     */
    public enum Status {
        /** Trống, sẵn sàng phục vụ */
        AVAILABLE,
        /** Đang có khách (thuộc 1 session) */
        OCCUPIED,
        /** Đã đặt trước */
        RESERVED,
        /** Legacy: tương đương OCCUPIED, để tương thích dữ liệu cũ */
        SERVING
    }

    /**
     * Kiểm tra bàn có đang trống không.
     * 
     * @return true nếu bàn trống và sẵn sàng phục vụ
     */
    public boolean isAvailable() {
        return currentSession == null && (status == Status.AVAILABLE || status == null);
    }
}