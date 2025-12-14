package com.project.fnb.modules.hrm.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.global.entity.User;
import com.project.fnb.modules.global.repository.UserRepository;
import com.project.fnb.modules.hrm.dto.ApplicationResponse;
import com.project.fnb.modules.hrm.dto.ApplyRequest;
import com.project.fnb.modules.hrm.entity.Application;
import com.project.fnb.modules.hrm.entity.Employee;
import com.project.fnb.modules.hrm.entity.JobPost;
import com.project.fnb.modules.hrm.repository.ApplicationRepository;
import com.project.fnb.modules.hrm.repository.JobPostRepository;

import lombok.RequiredArgsConstructor;

import java.util.List;

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
    private final UserRepository userRepository;
    private final JobPostRepository jobPostRepository;
    private final StaffService staffService;

    @Transactional
    public void applyToJob(String userId, ApplyRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(404, "User profile not found"));
        
        String tenantId = getTenantIdByJobPostId(request.getJobPostId());
        
        String oldContext = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            
            JobPost jobPost = jobPostRepository.findById(request.getJobPostId())
                    .orElseThrow(() -> new AppException(404, "Tin tuyển dụng không còn tồn tại"));

            if (!Boolean.TRUE.equals(jobPost.getIsActive())) {
                throw new AppException(400, "Tin này đã đóng tuyển dụng");
            }

            boolean alreadyApplied = applicationRepository.existsByUserIdAndJobPostId(userId, request.getJobPostId());
            if (alreadyApplied) {
                throw new AppException(400, "Bạn đã nộp đơn ứng tuyển cho công việc này rồi.");
            }

            Application app = Application.builder()
                    .user(user)
                    .jobPost(jobPost)
                    .message(request.getMessage())
                    .status(Application.Status.PENDING)
                    .build();
            
            applicationRepository.save(app);

        } finally {
             if (oldContext != null) TenantContext.setTenantId(oldContext);
             else TenantContext.clear();
        }
    }

    private String getTenantIdByJobPostId(Long jobPostId) {
        return jobPostRepository.findTenantIdById(jobPostId)
                .orElseThrow(() -> new AppException(404, "Tin tuyển dụng không tồn tại hoặc đã bị xóa"));
    }

    public List<ApplicationResponse> getApplicationsByJob(Long jobId) {
        return applicationRepository.findAll().stream()
                .filter(app -> app.getJobPost().getId().equals(jobId)) 
                .map(this::mapToAppResponse)
                .toList();
    }

    @Transactional
    public void processApplication(Integer applicationId, Application.Status newStatus) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new AppException(404, "Đơn ứng tuyển không tồn tại"));

        if (app.getStatus() != Application.Status.PENDING) {
            throw new AppException(400, "Đơn này đã được xử lý trước đó");
        }

        app.setStatus(newStatus);
        applicationRepository.save(app);

        if (newStatus == Application.Status.APPROVED) {
            staffService.createEmployeeFromUser(app.getUser().getId(), Employee.Role.STAFF);
        }
    }

    private ApplicationResponse mapToAppResponse(Application app) {
        return ApplicationResponse.builder()
                .id(app.getId())
                .candidateName(app.getUser().getFullName())
                .candidateEmail(app.getUser().getEmail())
                .candidateAvatar(app.getUser().getAvatarUrl())
                .candidatePhone(app.getUser().getPhone())
                .message(app.getMessage())
                .status(app.getStatus())
                .createdAt(app.getCreatedAt())
                .build();
    }
}