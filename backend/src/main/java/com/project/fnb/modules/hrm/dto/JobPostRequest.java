package com.project.fnb.modules.hrm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JobPostRequest {
    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    @NotBlank(message = "Mô tả công việc không được để trống")
    private String description;

    private Boolean isActive; 
}