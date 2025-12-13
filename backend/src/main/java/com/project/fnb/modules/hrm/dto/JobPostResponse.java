package com.project.fnb.modules.hrm.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class JobPostResponse {
    private Long id;
    private String title;
    private String description;
    private Boolean isActive;
    private String tenantName; 
    private String tenantLogo; 
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}