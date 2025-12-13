package com.project.fnb.modules.hrm.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.entity.User;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.global.repository.UserRepository;
import com.project.fnb.modules.hrm.dto.ApplyRequest;
import com.project.fnb.modules.hrm.entity.Application;
import com.project.fnb.modules.hrm.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý tuyển dụng (Recruitment) cho hệ thống HRM (Human Resource Management).
 * 
 * <p>Class này cung cấp các business logic cho quy trình tuyển dụng của tenant,
 * cho phép nhân viên/ứng viên ứng tuyển vào các quán hàng/tenant khác nhau.</p>
 * 
 * <p><b>Các tính năng chính:</b></p>
 * <ul>
 *   <li>Cho phép user ứng tuyển vào tenant khác (applyToTenant)</li>
 *   <li>Kiểm tra duplicate applications (tránh spam ứng tuyển)</li>
 *   <li>Quản lý trạng thái ứng tuyển (PENDING, APPROVED, REJECTED)</li>
 *   <li>Tự động set tenant context khi lưu application vào database</li>
 * </ul>
 * 
 * <p><b>Multi-Tenancy Context:</b></p>
 * <ul>
 *   <li>Application được lưu vào tenant-specific schema/bucket</li>
 *   <li>TenantContext được switch sang tenant đích trước khi save</li>
 *   <li>BaseEntity.TenantListener tự động gán tenantId từ TenantContext</li>
 *   <li>TenantContext được restore sau khi xong (trong finally block)</li>
 * </ul>
 * 
 * <p><b>Dependencies:</b></p>
 * <ul>
 *   <li>ApplicationRepository - quản lý application records</li>
 *   <li>TenantRepository - kiểm tra tenant tồn tại</li>
 *   <li>UserRepository - kiểm tra user tồn tại</li>
 *   <li>TenantContext - ThreadLocal tenant ID storage</li>
 * </ul>
 * 
 * <p><b>Workflow:</b></p>
 * <ul>
 *   <li>Ứng viên gửi ApplyRequest (tenantId, message)</li>
 *   <li>Service kiểm tra tenant và user tồn tại</li>
 *   <li>Service kiểm tra duplicate application (tránh spam)</li>
 *   <li>Service tạo Application record với status=PENDING</li>
 *   <li>Tenant admin sau đó có thể APPROVE/REJECT application</li>
 * </ul>
 * 
 * @author Project Team
 * @version 1.0
 * @see Application
 * @see ApplyRequest
 * @see TenantContext
 * @see ApplicationRepository
 */
@Service
@RequiredArgsConstructor
public class RecruitmentService {

    private final ApplicationRepository applicationRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    /**
     * Cho phép user ứng tuyển vào một tenant (quán hàng).
     * 
     * <p>Endpoint này cho phép user (nhân viên/ứng viên) ứng tuyển vào tenant khác.
     * Ứng dụng được tạo với trạng thái PENDING, chờ tenant admin phê duyệt.</p>
     * 
     * <p><b>Quy trình chi tiết:</b></p>
     * <ol>
     *   <li>Kiểm tra tenant tồn tại theo tenantId từ request</li>
     *   <li>Kiểm tra user profile tồn tại trong hệ thống</li>
     *   <li>Switch TenantContext sang tenant đích (quan trọng cho multi-tenancy)</li>
     *   <li>Kiểm tra xem user đã ứng tuyển chưa (Status=PENDING)</li>
     *   <li>Nếu đã ứng tuyển, throw exception (tránh spam)</li>
     *   <li>Tạo Application record mới với status=PENDING</li>
     *   <li>BaseEntity.TenantListener tự động gán tenantId từ TenantContext</li>
     *   <li>Lưu Application vào database</li>
     *   <li>Restore TenantContext cũ (hoặc clear) trong finally block</li>
     * </ol>
     * 
     * <p><b>Multi-Tenancy Logic:</b></p>
     * <ul>
     *   <li>TenantContext được lưu lại trước khi switch (oldContext)</li>
     *   <li>TenantContext.setTenantId(tenant.getId()) switch sang tenant đích</li>
     *   <li>Trong try block, tất cả queries dùng tenant context mới</li>
     *   <li>Application record được lưu vào tenant-specific table (via BaseEntity listener)</li>
     *   <li>Trong finally, TenantContext được restore về oldContext</li>
     *   <li>Đảm bảo request sau không bị ảnh hưởng bởi context này</li>
     * </ul>
     * 
     * <p><b>Duplicate Prevention:</b></p>
     * <ul>
     *   <li>Kiểm tra findByUserIdAndStatus(userId, PENDING)</li>
     *   <li>Nếu tồn tại, throw AppException(400, "Bạn đã ứng tuyển...")</li>
     *   <li>Tránh user spam ứng tuyển nhiều lần</li>
     * </ul>
     * 
     * <p><b>Transaction:</b></p>
     * <ul>
     *   <li>@Transactional đảm bảo consistency</li>
     *   <li>Nếu exception, tất cả changes rollback</li>
     *   <li>TenantContext restore luôn diễn ra (trong finally)</li>
     * </ul>
     * 
     * <p><b>Response Handling:</b></p>
     * <ul>
     *   <li>Method void - không trả về Application (mục đích là set status PENDING)</li>
     *   <li>Client có thể query Application sau để xem trạng thái</li>
     *   <li>Tenant admin có thể xem và phê duyệt trong admin panel</li>
     * </ul>
     * 
     * @param userId User ID của ứng viên (từ JWT token, bắt buộc)
     * @param request ApplyRequest chứa:
     *                - tenantId: ID của tenant ứng viên muốn ứng tuyển (bắt buộc)
     *                - message: Thư ứng tuyển (optional, mô tả tại sao muốn join)
     * 
     * @throws AppException(404) nếu tenant với ID không tồn tại
     * @throws AppException(404) nếu user profile chưa được đồng bộ từ Keycloak
     * @throws AppException(400) nếu user đã ứng tuyển vào tenant này (Status=PENDING)
     * @throws DataIntegrityViolationException nếu dữ liệu invalid
     * @throws RuntimeException nếu xảy ra lỗi khi save Application
     * 
     * @see Application
     * @see ApplyRequest
     * @see TenantContext
     * @see Application.Status
     */
    @Transactional
    public void applyToTenant(String userId, ApplyRequest request) {
        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new AppException(404, "Quán không tồn tại"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(404, "User profile chưa đồng bộ"));

        String oldContext = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenant.getId());

            boolean alreadyApplied = applicationRepository.findByUserIdAndStatus(userId, Application.Status.PENDING).isPresent();
            if (alreadyApplied) {
                throw new AppException(400, "Bạn đã ứng tuyển vào quán này rồi, vui lòng chờ phản hồi.");
            }

            Application app = Application.builder()
                    .user(user)
                    .message(request.getMessage())
                    .status(Application.Status.PENDING)
                    .build();
            applicationRepository.save(app);

        } finally {
            if (oldContext != null) TenantContext.setTenantId(oldContext);
            else TenantContext.clear();
        }
    }
}