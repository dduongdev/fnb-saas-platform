package com.project.fnb.modules.global.controller;

import com.project.fnb.modules.global.dto.CreateTenantRequest;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    public Tenant createTenant(@RequestBody @Valid CreateTenantRequest request, 
                               @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        return tenantService.createTenant(request, userId);
    }
}