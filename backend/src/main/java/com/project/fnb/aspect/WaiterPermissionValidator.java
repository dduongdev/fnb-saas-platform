package com.project.fnb.aspect;

import com.project.fnb.infrastructure.security.AccessKeyUserDetails;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class WaiterPermissionValidator implements PermissionValidator {

    @Autowired
    private TenantRepository tenantRepository;

    @Override
    public boolean validate(Authentication authentication, String tenantId) {
        if (authentication == null || tenantId == null) {
            return false;
        }

        // Access key waiter flow
        Object principal = authentication.getPrincipal();
        if (principal instanceof AccessKeyUserDetails) {
            AccessKeyUserDetails accessKey = (AccessKeyUserDetails) principal;
            if (!tenantId.equals(accessKey.getTenantId())) {
                return false;
            }
            return "WAITER".equalsIgnoreCase(accessKey.getRole().replace("ROLE_", ""));
        }

        // Owner vẫn được phép cho endpoint dùng cả owner + waiter
        if (principal instanceof Jwt || principal instanceof com.project.fnb.modules.global.entity.User) {
            String userId = null;
            if (principal instanceof Jwt) {
                userId = ((Jwt) principal).getSubject();
            } else {
                userId = ((com.project.fnb.modules.global.entity.User) principal).getId();
            }
            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            return tenant != null && tenant.getOwnerId() != null && tenant.getOwnerId().equals(userId);
        }

        return false;
    }
}
