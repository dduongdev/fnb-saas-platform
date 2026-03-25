package com.project.fnb.modules.global.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.global.dto.AccessKeyDto;
import com.project.fnb.modules.global.dto.CreateAccessKeyRequest;
import com.project.fnb.modules.global.service.AccessKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/{tenantId}/access-keys")
@RequiredArgsConstructor
public class AccessKeyController {

    private final AccessKeyService accessKeyService;

    @PostMapping
    public ApiResponse<AccessKeyDto> createKey(
            @PathVariable String tenantId,
            @Valid @RequestBody CreateAccessKeyRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String currentUserId = jwt.getSubject();
        return ApiResponse.success(accessKeyService.createKey(tenantId, currentUserId, request));
    }

    @GetMapping
    public ApiResponse<List<AccessKeyDto>> getKeys(
            @PathVariable String tenantId,
            @AuthenticationPrincipal Jwt jwt) {
        String currentUserId = jwt.getSubject();
        return ApiResponse.success(accessKeyService.getKeysByTenant(tenantId, currentUserId));
    }

    @GetMapping("/roles")
    public ApiResponse<List<String>> getRoles(@PathVariable String tenantId) {
        return ApiResponse.success(accessKeyService.getAccessRoles());
    }

    @DeleteMapping("/{keyId}")
    public ApiResponse<Void> revokeKey(
            @PathVariable String tenantId,
            @PathVariable String keyId,
            @AuthenticationPrincipal Jwt jwt) {
        String currentUserId = jwt.getSubject();
        accessKeyService.revokeKey(tenantId, keyId, currentUserId);
        return ApiResponse.success(null);
    }
}
