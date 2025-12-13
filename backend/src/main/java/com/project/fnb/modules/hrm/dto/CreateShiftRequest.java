package com.project.fnb.modules.hrm.dto;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CreateShiftRequest {
    private Integer employeeId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String note;
}