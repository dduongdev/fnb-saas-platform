package com.project.fnb.modules.hrm.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.hrm.dto.ApplyRequest;
import com.project.fnb.modules.hrm.service.RecruitmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}