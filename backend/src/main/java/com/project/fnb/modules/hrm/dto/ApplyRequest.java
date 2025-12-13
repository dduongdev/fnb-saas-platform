package com.project.fnb.modules.hrm.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApplyRequest {
    @NotNull(message = "Chọn tin tuyển dụng")
    private Long jobPostId; 
    private String message;
}