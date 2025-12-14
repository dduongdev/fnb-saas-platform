package com.project.fnb.modules.global.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.hrm.dto.JobPostResponse;
import com.project.fnb.modules.hrm.entity.JobPost;
import com.project.fnb.modules.hrm.repository.JobPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/jobs")
@RequiredArgsConstructor
public class PublicJobController {

    private final JobPostRepository jobPostRepository;
    private final TenantRepository tenantRepository;

    @GetMapping
    public ApiResponse<Page<JobPostResponse>> getAllJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        
        // 1. Lấy danh sách Job (Native Query bypass filter)
        Page<JobPost> jobPage = jobPostRepository.findAllPublicJobs(pageable);

        // 2. Lấy danh sách Tenant ID để query 1 lần (Bulk Read)
        Set<String> tenantIds = jobPage.getContent().stream()
                .map(JobPost::getTenantId)
                .collect(Collectors.toSet());
        
        // 3. Map Tenant ID -> Tenant Object
        Map<String, Tenant> tenantMap = tenantRepository.findAllById(tenantIds).stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));

        // 4. Map sang DTO
        Page<JobPostResponse> response = jobPage.map(job -> {
            Tenant t = tenantMap.get(job.getTenantId());
            return JobPostResponse.builder()
                    .id(job.getId())
                    .title(job.getTitle())
                    .description(job.getDescription())
                    .isActive(job.getIsActive())
                    .createdAt(job.getCreatedAt())
                    .updatedAt(job.getUpdatedAt())
                    .tenantName(t != null ? t.getName() : "Unknown")
                    .tenantLogo(t != null ? t.getLogoUrl() : null)
                    .build();
        });

        return ApiResponse.success(response);
    }
}