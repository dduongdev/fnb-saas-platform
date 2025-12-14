package com.project.fnb.modules.hrm.dto;

import com.project.fnb.modules.hrm.entity.Employee;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class EmployeeResponse {
    private Integer id;
    private Employee.Role role;
    private Employee.Status status;
    private LocalDate joinedAt;

    private String userId;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
}