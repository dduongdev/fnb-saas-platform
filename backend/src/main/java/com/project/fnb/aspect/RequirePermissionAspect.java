package com.project.fnb.aspect;

import com.project.fnb.infrastructure.security.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequirePermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(RequirePermissionAspect.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Around("@annotation(requirePermission)")
    public Object around(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String tenantId = TenantContext.getTenantId();

        for (Class<? extends PermissionValidator> validatorClass : requirePermission.value()) {
            PermissionValidator validator = applicationContext.getBean(validatorClass);
            boolean ok = validator.validate(authentication, tenantId);
            log.debug("[RequirePermissionAspect] validator {} => {}", validatorClass.getSimpleName(), ok);
            if (ok) {
                return joinPoint.proceed();
            }
        }

        throw new RuntimeException("Bạn không có quyền thực hiện thao tác này");
    }
}
