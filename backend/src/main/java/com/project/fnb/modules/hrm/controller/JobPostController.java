package com.project.fnb.modules.hrm.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.hrm.dto.JobPostRequest;
import com.project.fnb.modules.hrm.dto.JobPostResponse;
import com.project.fnb.modules.hrm.service.JobPostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hrm/jobs")
@RequiredArgsConstructor
public class JobPostController {

    private final JobPostService jobPostService;

    @GetMapping
    public ApiResponse<Page<JobPostResponse>> getMyJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(jobPostService.getMyJobPosts(pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<JobPostResponse> getJobDetail(@PathVariable Long id) {
        return ApiResponse.success(jobPostService.getJobPostDetail(id));
    }

    @PostMapping
    public ApiResponse<JobPostResponse> createJob(@RequestBody @Valid JobPostRequest request) {
        return ApiResponse.success(jobPostService.createJobPost(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<JobPostResponse> updateJob(
            @PathVariable Long id,
            @RequestBody JobPostRequest request) {
        return ApiResponse.success(jobPostService.updateJobPost(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteJob(@PathVariable Long id) {
        jobPostService.deleteJobPost(id);
        return ApiResponse.success("Đã xóa tin tuyển dụng");
    }
}