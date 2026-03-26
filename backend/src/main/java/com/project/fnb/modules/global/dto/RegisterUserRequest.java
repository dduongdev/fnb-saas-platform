package com.project.fnb.modules.global.dto;

import lombok.Data;

@Data
public class RegisterUserRequest {
    private String username;
    private String email;
    private String password;
    private String fullName;
}
