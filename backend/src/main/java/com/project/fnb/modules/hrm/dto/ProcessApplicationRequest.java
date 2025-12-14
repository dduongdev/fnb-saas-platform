package com.project.fnb.modules.hrm.dto;

import com.project.fnb.modules.hrm.entity.Application;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProcessApplicationRequest {
    @NotNull(message = "Vui lòng chọn trạng thái")
    private Application.Status status; 
}