package com.project.fnb.common;

import com.project.fnb.infrastructure.security.TenantContext;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Lớp cơ sở (Base Entity) cho tất cả các entity trong hệ thống.
 * 
 * <p>Class này cung cấp các trường chung và logic chung cho toàn bộ các entity,
 * bao gồm quản lý tenant, thời gian tạo/cập nhật, và soft delete.</p>
 * 
 * <p><b>Tính năng chính:</b></p>
 * <ul>
 *   <li><b>Multi-tenancy:</b> Mỗi entity tự động ghi lại tenant ID từ TenantContext khi được tạo</li>
 *   <li><b>Audit Trail:</b> Tự động ghi lại thời gian tạo và cập nhật entity</li>
 *   <li><b>Soft Delete:</b> Thay vì xóa vật lý, entity được đánh dấu is_deleted = true</li>
 *   <li><b>Automatic Filtering:</b> Các query tự động loại bỏ entity đã bị soft delete</li>
 * </ul>
 * 
 * <p><b>Cơ chế hoạt động:</b></p>
 * <ol>
 *   <li>TenantListener.setTenant() tự động được gọi bằng @PrePersist trước khi save</li>
 *   <li>Tenant ID được lấy từ TenantContext và gán vào entity</li>
 *   <li>createdAt và updatedAt tự động được cập nhật bởi @CreationTimestamp/@UpdateTimestamp</li>
 *   <li>@SQLRestriction đảm bảo các query không trả về entity bị soft delete</li>
 *   <li>@SQLDelete ghi đè lệnh DELETE thành UPDATE is_deleted = true</li>
 * </ol>
 * 
 * <p><b>Lưu ý:</b> Đây là MappedSuperclass, không phải Entity, nên không có bảng riêng.
 * Các entity kế thừa từ class này sẽ có các trường này trong bảng của chúng.</p>
 * 
 * @author Project Team
 * @version 1.0
 * @see TenantContext
 * @see TenantListener
 */
@Getter
@Setter
@MappedSuperclass 
@EntityListeners(BaseEntity.TenantListener.class) 
@SQLDelete(sql = "UPDATE categories SET is_deleted = true WHERE id = ?") 
@SQLRestriction("is_deleted = false") 

@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class BaseEntity {

    /**
     * ID của tenant mà entity này thuộc về.
     * 
     * <p>Trường này tự động được thiết lập bởi TenantListener khi entity được tạo.
     * Giá trị được lấy từ TenantContext.getTenantId() tại thời điểm persist.
     * Trường này không thể cập nhật sau khi tạo (updatable = false).</p>
     * 
     * <p><b>Mục đích:</b> Đảm bảo dữ liệu của mỗi tenant bị cách ly hoàn toàn.
     * Mỗi entity phải biết nó thuộc tenant nào để có thể lọc dữ liệu đúng.</p>
     */
    @Column(name = "tenant_id", updatable = false) 
    private String tenantId;

    /**
     * Thời điểm entity được tạo.
     * 
     * <p>Trường này tự động được thiết lập bởi Hibernate khi entity được persist
     * lần đầu tiên (do @CreationTimestamp). Sau khi tạo, trường này không thể
     * được cập nhật (updatable = false).</p>
     * 
     * <p><b>Kiểu dữ liệu:</b> LocalDateTime (chứa cả ngày và giờ)</p>
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Thời điểm entity được cập nhật lần cuối.
     * 
     * <p>Trường này tự động được cập nhật bởi Hibernate mỗi khi entity được
     * sửa đổi và persist (do @UpdateTimestamp). Lần đầu tiên sẽ có giá trị
     * bằng createdAt, sau đó sẽ được cập nhật mỗi khi entity thay đổi.</p>
     * 
     * <p><b>Kiểu dữ liệu:</b> LocalDateTime (chứa cả ngày và giờ)</p>
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Cờ soft delete để đánh dấu entity đã bị xóa.
     * 
     * <p><b>Mục đích:</b> Thay vì xóa vật lý, entity được đánh dấu là deleted.
     * Điều này cho phép:</p>
     * <ul>
     *   <li>Giữ lại dữ liệu lịch sử cho mục đích audit</li>
     *   <li>Khôi phục dữ liệu nếu cần thiết</li>
     *   <li>Tuân thủ các yêu cầu về bảo vệ dữ liệu</li>
     * </ul>
     * 
     * <p><b>Cơ chế:</b></p>
     * <ul>
     *   <li>Giá trị mặc định: false (entity hoạt động)</li>
     *   <li>Khi gọi repository.delete(), entity được update với is_deleted = true (không bị xóa vật lý)</li>
     *   <li>@SQLRestriction tự động loại bỏ các entity có is_deleted = true khỏi query results</li>
     * </ul>
     */
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    /**
     * JPA Entity Listener để tự động thiết lập Tenant ID cho entity.
     * 
     * <p>Class này sử dụng @PrePersist callback để bắt sự kiện trước khi
     * entity được lưu vào database lần đầu tiên. Tại thời điểm này,
     * tenant ID sẽ được lấy từ TenantContext và gán vào entity.</p>
     * 
     * <p><b>Cơ chế hoạt động:</b></p>
     * <ol>
     *   <li>Khi một entity mới được persist, @PrePersist callback được kích hoạt</li>
     *   <li>Lấy tenant ID từ TenantContext.getTenantId()</li>
     *   <li>Nếu tenant ID không null, thiết lập nó vào entity</li>
     *   <li>Entity được lưu với tenant ID đã thiết lập</li>
     * </ol>
     * 
     * <p><b>Lưu ý quan trọng:</b> Tenant ID phải đã được thiết lập trong TenantContext
     * trước khi tạo entity, nếu không entity sẽ được tạo mà không có tenant ID.</p>
     * 
     * @see TenantContext
     * @see #setTenant(BaseEntity)
     */
    public static class TenantListener {
        /**
         * Callback được gọi trước khi entity được persist vào database.
         * 
         * <p>Phương thức này tự động thiết lập tenant ID từ TenantContext
         * vào entity. Được gọi bởi JPA trước khi INSERT statement được thực hiện.</p>
         * 
         * <p><b>Quy trình:</b></p>
         * <ol>
         *   <li>Lấy current tenant ID từ TenantContext.getTenantId()</li>
         *   <li>Kiểm tra nếu tenant ID không null</li>
         *   <li>Gán tenant ID vào entity</li>
         * </ol>
         * 
         * <p><b>Ngoại lệ:</b> Nếu tenant ID là null (TenantContext chưa được thiết lập),
         * entity sẽ được persist mà không có tenant ID. Điều này có thể dẫn đến
         * lỗi dữ liệu, vì vậy cần đảm bảo TenantFilter hoạt động đúng.</p>
         * 
         * @param entity Entity chuẩn bị được persist
         */
        @PrePersist 
        public void setTenant(BaseEntity entity) {
            String currentTenant = TenantContext.getTenantId();
            if (currentTenant != null) {
                entity.setTenantId(currentTenant);
            }
        }
    }
}