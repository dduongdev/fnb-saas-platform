package com.project.fnb.modules.hrm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApplyRequest {
    @NotBlank(message = "Vui lòng chọn quán muốn ứng tuyển")
    private String tenantId;

    @NotBlank(message = "Vui lòng nhập lời nhắn tới chủ quán")
    private String message;
}