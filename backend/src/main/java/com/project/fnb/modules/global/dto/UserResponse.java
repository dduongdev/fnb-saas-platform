package com.project.fnb.modules.global.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {
    private String id;
    private String email;
    private String fullName;
    private String avatarUrl;
    private Integer trustScore;
    private String phone;
}