package com.project.fnb.modules.hrm.service;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.IdentityService;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.entity.User;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.global.repository.UserRepository;
import com.project.fnb.modules.hrm.dto.EmployeeResponse;
import com.project.fnb.modules.hrm.entity.Employee;
import com.project.fnb.modules.hrm.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final IdentityService identityService;
    private final TenantRepository tenantRepository;

    @Transactional
    public void createEmployeeFromUser(String userId, Employee.Role role) {
        String tenantId = TenantContext.getTenantId();

        if (employeeRepository.findByUserIdAndTenantId(userId, tenantId).isPresent()) {
            return; 
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(404, "User not found"));

        Employee employee = Employee.builder()
                .user(user)
                .role(role)
                .status(Employee.Status.ACTIVE)
                .joinedAt(LocalDate.now())
                .build();
        
        employeeRepository.save(employee);

        identityService.addUserToGroup(userId, tenantId);
    }

    @Transactional
    public void removeStaff(Integer employeeId) {
        String tenantId = TenantContext.getTenantId();

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new AppException(404, "Nhân viên không tồn tại"));

        if (!employee.getTenantId().equals(tenantId)) {
            throw new AppException(403, "Không có quyền truy cập nhân viên này");
        }

        identityService.removeUserFromGroup(employee.getUser().getId(), tenantId);

        employee.setStatus(Employee.Status.RESIGNED);
        employeeRepository.save(employee);
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> getEmployees(Pageable pageable, String requesterUserId) {
        String tenantId = TenantContext.getTenantId();
        
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(404, "Tenant not found"));

        if (!tenant.getOwnerId().equals(requesterUserId)) {
            throw new AppException(403, "Access Denied: Chỉ chủ quán mới có quyền xem danh sách nhân viên.");
        }

        Page<Employee> employeePage = employeeRepository.findAllWithUser(pageable);
        
        return employeePage.map(this::mapToResponse);
    }

    private EmployeeResponse mapToResponse(Employee employee) {
        return EmployeeResponse.builder()
                .id(employee.getId())
                .role(employee.getRole())
                .status(employee.getStatus())
                .joinedAt(employee.getJoinedAt())
                .userId(employee.getUser().getId())
                .fullName(employee.getUser().getFullName())
                .email(employee.getUser().getEmail())
                .phone(employee.getUser().getPhone())
                .avatarUrl(employee.getUser().getAvatarUrl())
                .build();
    }
}
