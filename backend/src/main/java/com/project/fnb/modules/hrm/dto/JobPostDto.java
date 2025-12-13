package com.project.fnb.modules.hrm.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JobPostDto {
    private Long id;
    private String title;
    private String description;
    private Boolean isActive;
    private String tenantName; 
    private String tenantLogo; 
}