package com.project.fnb.modules.global.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.common.exception.AppException;
import com.project.fnb.modules.global.dto.LoginRequest;
import com.project.fnb.modules.global.dto.LoginResponse;
import com.project.fnb.modules.global.dto.RegisterUserRequest;
import com.project.fnb.modules.global.dto.UserResponse;
import com.project.fnb.modules.global.service.AuthService;
import com.project.fnb.modules.global.service.UserService;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<String> registerUser(@RequestBody RegisterUserRequest request) {
        authService.registerUser(request);
        return ApiResponse.success("Đăng ký thành công");
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}