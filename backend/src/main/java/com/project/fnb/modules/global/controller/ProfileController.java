package com.project.fnb.modules.global.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.global.dto.AvailabilityDto;
import com.project.fnb.modules.global.service.UserAvailabilityService;
import com.project.fnb.modules.global.service.UserService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserAvailabilityService availabilityService;
    private final UserService userService;

    @GetMapping("/availabilities")
    public ApiResponse<List<AvailabilityDto>> getMyAvailabilities(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        return ApiResponse.success(availabilityService.getAvailability(userId));
    }

    @PutMapping("/availabilities")
    public ApiResponse<List<AvailabilityDto>> updateAvailabilities(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody List<AvailabilityDto> dtos) {
        String userId = jwt.getSubject();
        return ApiResponse.success(availabilityService.updateAvailability(userId, dtos));
    }

    @PostMapping(value = "/avatar", consumes = { "multipart/form-data" })
    public ApiResponse<String> uploadAvatar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file) {
        
        String userId = jwt.getSubject();
        String newUrl = userService.updateAvatar(userId, file);
        
        return ApiResponse.success(newUrl);
    }
}