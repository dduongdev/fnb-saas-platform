package com.project.fnb.modules.hrm.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.hrm.dto.EmployeeResponse;
import com.project.fnb.modules.hrm.service.StaffService;

import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {
    private final StaffService staffService;

    @DeleteMapping("/{id}")
    public ApiResponse<String> removeStaff(@PathVariable Integer id) {
        staffService.removeStaff(id);
        return ApiResponse.success("Đã xóa nhân viên khỏi quán và thu hồi quyền truy cập.");
    }

    @GetMapping
    public ApiResponse<Page<EmployeeResponse>> getEmployees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Pageable pageable = PageRequest.of(page, size);
        String userId = jwt.getSubject();
        return ApiResponse.success(staffService.getEmployees(pageable, userId));
    }
}
