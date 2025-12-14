package com.project.fnb.modules.hrm.dto;

import com.project.fnb.modules.hrm.entity.Application;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ApplicationResponse {
    private Integer id;
    private String candidateName;
    private String candidateAvatar;
    private String candidateEmail;
    private String candidatePhone;
    private String message;
    private Application.Status status;
    private LocalDateTime createdAt;
}