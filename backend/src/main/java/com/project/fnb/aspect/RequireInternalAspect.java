package com.project.fnb.aspect;

import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.infrastructure.security.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import com.project.fnb.infrastructure.security.AccessKeyUserDetails;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aspect xác thực nội bộ: chỉ cho phép user là owner hoặc waiter của tenant hiện tại.
 */
@Aspect
@Component
public class RequireInternalAspect {
    private static final Logger log = LoggerFactory.getLogger(RequireInternalAspect.class);

    @Autowired
    private TenantRepository tenantRepository;

    @Around("@annotation(com.project.fnb.aspect.RequireInternal)")
    public Object checkInternal(ProceedingJoinPoint joinPoint) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("[RequireInternalAspect] Authentication: {}", authentication);
        if (authentication == null) {
            log.error("[RequireInternalAspect] authentication is null");
            throw new RuntimeException("Chưa đăng nhập");
        }
        Object principal = authentication.getPrincipal();
        String tenantId = TenantContext.getTenantId();
        log.info("[RequireInternalAspect] TenantId from context: {}", tenantId);
        if (tenantId == null) {
            log.error("[RequireInternalAspect] tenantId is null");
            throw new RuntimeException("Không xác định được tenant hiện tại");
        }
        if (principal instanceof AccessKeyUserDetails) {
            AccessKeyUserDetails accessKeyUser = (AccessKeyUserDetails) principal;
            if (!tenantId.equals(accessKeyUser.getTenantId())) {
                log.error("[RequireInternalAspect] AccessKey tenant mismatch: {} != {}", tenantId, accessKeyUser.getTenantId());
                throw new RuntimeException("Bạn không thuộc quán này");
            }
            log.info("[RequireInternalAspect] AccessKeyUserDetails hợp lệ cho tenant {}", tenantId);
            return joinPoint.proceed();
        }
        String userId = null;
        if (principal instanceof com.project.fnb.modules.global.entity.User) {
            userId = ((com.project.fnb.modules.global.entity.User) principal).getId();
        } else if (principal instanceof Jwt) {
            userId = ((Jwt) principal).getSubject();
        }
        if (userId == null) {
            log.error("[RequireInternalAspect] Cannot extract userId from principal: {}", principal);
            throw new RuntimeException("Chưa đăng nhập");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant không tồn tại"));
        if (!userId.equals(tenant.getOwnerId()) && (tenant.getAccessKeys() == null || !tenant.getAccessKeys().contains(userId))) {
            log.error("[RequireInternalAspect] user {} is not internal of tenant {}", userId, tenantId);
            throw new RuntimeException("Bạn không thuộc quán này");
        }
        log.info("[RequireInternalAspect] user {} is internal of tenant {}", userId, tenantId);
        return joinPoint.proceed();
    }
}
