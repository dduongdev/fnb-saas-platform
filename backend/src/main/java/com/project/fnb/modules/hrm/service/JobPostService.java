package com.project.fnb.modules.hrm.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.hrm.dto.JobPostRequest;
import com.project.fnb.modules.hrm.dto.JobPostResponse;
import com.project.fnb.modules.hrm.entity.JobPost;
import com.project.fnb.modules.hrm.repository.JobPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobPostService {

    private final JobPostRepository jobPostRepository;

    @Transactional
    public JobPostResponse createJobPost(JobPostRequest request) {
        if (TenantContext.getTenantId() == null) {
            throw new AppException(500, "Lỗi hệ thống: Không xác định được danh tính quán (Missing Tenant Context)");
        }

        JobPost jobPost = JobPost.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        JobPost savedPost = jobPostRepository.save(jobPost);

        return mapToResponse(savedPost);
    }

    public Page<JobPostResponse> getMyJobPosts(Pageable pageable) {
        Page<JobPost> postPage = jobPostRepository.findAllByOrderByCreatedAtDesc(pageable);
        
        return postPage.map(this::mapToResponse);
    }

    public JobPostResponse getJobPostDetail(Long id) {
        JobPost post = jobPostRepository.findById(id)
                .orElseThrow(() -> new AppException(404, "Tin tuyển dụng không tồn tại"));
        return mapToResponse(post);
    }

    @Transactional
    public JobPostResponse updateJobPost(Long id, JobPostRequest request) {
        JobPost post = jobPostRepository.findById(id)
                .orElseThrow(() -> new AppException(404, "Tin tuyển dụng không tồn tại"));

        if (request.getTitle() != null) post.setTitle(request.getTitle());
        if (request.getDescription() != null) post.setDescription(request.getDescription());
        if (request.getIsActive() != null) post.setIsActive(request.getIsActive());

        JobPost updatedPost = jobPostRepository.save(post);
        return mapToResponse(updatedPost);
    }

    @Transactional
    public void deleteJobPost(Long id) {
        JobPost post = jobPostRepository.findById(id)
                .orElseThrow(() -> new AppException(404, "Tin tuyển dụng không tồn tại"));
        jobPostRepository.delete(post); 
    }

    private JobPostResponse mapToResponse(JobPost entity) {
        return JobPostResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}