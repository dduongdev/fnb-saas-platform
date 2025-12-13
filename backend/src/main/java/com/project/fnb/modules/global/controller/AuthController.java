package com.project.fnb.modules.global.controller;

import com.project.fnb.modules.global.dto.UserResponse;
import com.project.fnb.modules.global.service.UserService;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * API này được gọi sau khi Login Keycloak thành công.
     * Nhiệm vụ: Đồng bộ User từ Token vào DB MySQL của hệ thống.
     */
    @PostMapping("/sync")
    public UserResponse syncUser(@AuthenticationPrincipal Jwt jwt) {
        return userService.syncUserFromToken(jwt);
    }
}