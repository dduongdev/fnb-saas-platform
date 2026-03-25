package com.project.fnb.aspect;

import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class OwnerPermissionValidator implements PermissionValidator {

    @Autowired
    private TenantRepository tenantRepository;

    @Override
    public boolean validate(Authentication authentication, String tenantId) {
        if (authentication == null || tenantId == null) {
            return false;
        }

        String userId = null;
        if (authentication.getPrincipal() instanceof Jwt) {
            userId = ((Jwt) authentication.getPrincipal()).getSubject();
        } else if (authentication.getPrincipal() instanceof com.project.fnb.modules.global.entity.User) {
            userId = ((com.project.fnb.modules.global.entity.User) authentication.getPrincipal()).getId();
        }

        if (userId == null) {
            return false;
        }

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        return tenant != null && userId.equals(tenant.getOwnerId());
    }
}
