package com.project.fnb.aspect;

import org.springframework.security.core.Authentication;

public interface PermissionValidator {
    boolean validate(Authentication authentication, String tenantId);
}
