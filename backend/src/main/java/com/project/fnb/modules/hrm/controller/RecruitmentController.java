package com.project.fnb.modules.hrm.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.hrm.dto.ApplicationResponse;
import com.project.fnb.modules.hrm.dto.ApplyRequest;
import com.project.fnb.modules.hrm.dto.ProcessApplicationRequest;
import com.project.fnb.modules.hrm.service.RecruitmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.fnb.modules.hrm.entity.Application;

@RestController
@RequestMapping("/api/recruitment")
@RequiredArgsConstructor
public class RecruitmentController {

    private final RecruitmentService recruitmentService;

    @PostMapping("/apply")
    public ApiResponse<String> applyJob(@AuthenticationPrincipal Jwt jwt, 
                                        @RequestBody @Valid ApplyRequest request) {
        String userId = jwt.getSubject();
        recruitmentService.applyToJob(userId, request);
        return ApiResponse.success("Đã gửi đơn ứng tuyển thành công!");
    }

    @GetMapping("/jobs/{jobId}/applications")
    public ApiResponse<List<ApplicationResponse>> getApplications(@PathVariable Long jobId) {
        return ApiResponse.success(recruitmentService.getApplicationsByJob(jobId));
    }

    @PatchMapping("/applications/{id}")
    public ApiResponse<String> processApplication(
            @PathVariable Integer id,
            @RequestBody @Valid ProcessApplicationRequest request) {
        
        recruitmentService.processApplication(id, request.getStatus());
        
        String msg = request.getStatus() == Application.Status.APPROVED 
                ? "Đã duyệt đơn và thêm nhân viên vào hệ thống" 
                : "Đã từ chối đơn ứng tuyển";
        return ApiResponse.success(msg);
    }
}