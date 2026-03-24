package com.project.fnb.aspect;

import com.project.fnb.aspect.RequireWaiter;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.global.entity.User;
import org.springframework.security.oauth2.jwt.Jwt;
import com.project.fnb.infrastructure.security.AccessKeyUserDetails;
import com.project.fnb.infrastructure.security.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
@Component
public class RequireWaiterAspect {
    private static final Logger log = LoggerFactory.getLogger(RequireWaiterAspect.class);
    @Autowired
    private TenantRepository tenantRepository;

    @Around("@annotation(com.project.fnb.aspect.RequireWaiter)")
    public Object checkWaiter(ProceedingJoinPoint joinPoint) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("[RequireWaiterAspect] Authentication: {}", authentication);
        if (authentication == null) {
            log.error("[RequireWaiterAspect] authentication is null");
            throw new RuntimeException("Chưa đăng nhập");
        }
        Object principal = authentication.getPrincipal();
        log.info("[RequireWaiterAspect] Principal: {} ({})", principal, principal != null ? principal.getClass() : null);
        String userId = null;
        if (principal instanceof User) {
            userId = ((User) principal).getId();
        } else if (principal instanceof Jwt) {
            userId = ((Jwt) principal).getSubject();
        } else if (principal instanceof AccessKeyUserDetails) {
            userId = ((AccessKeyUserDetails) principal).getAccessKeyId(); // hoặc getUsername()
        }
        if (userId == null) {
            log.error("[RequireWaiterAspect] Cannot extract userId from principal: {}", principal);
            throw new RuntimeException("Chưa đăng nhập");
        }
        String tenantId = TenantContext.getTenantId();
        log.info("[RequireWaiterAspect] TenantId from context: {}", tenantId);
        if (tenantId == null) {
            log.error("[RequireWaiterAspect] tenantId is null");
            throw new RuntimeException("Không xác định được tenant hiện tại");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant không tồn tại"));
        if (!tenant.getAccessKeys().contains(userId)) {
            log.error("[RequireWaiterAspect] user {} is not waiter of tenant {}", userId, tenantId);
            throw new RuntimeException("Bạn không phải nhân viên quán này");
        }
        log.info("[RequireWaiterAspect] user {} is waiter of tenant {}", userId, tenantId);
        return joinPoint.proceed();
    }
}
