package com.project.fnb.modules.pos.service;

import com.project.fnb.infrastructure.security.AccessKeyUserDetails;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.pos.entity.PosActionAudit;
import com.project.fnb.modules.pos.repository.PosActionAuditRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PosActionAuditService {

    private final PosActionAuditRepository repository;

    public PosActionAuditService(PosActionAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String action, String targetType, String targetId, BigDecimal amount, String note, Long sessionId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String userId = null;
        String userType = "OWNER";
        String accessKeyId = null;
        String accessKeyRole = null;

        if (authentication != null && authentication.getPrincipal() != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof AccessKeyUserDetails accessKeyUser) {
                userType = "ACCESS_KEY";
                accessKeyId = accessKeyUser.getAccessKeyId();
                accessKeyRole = accessKeyUser.getRole();
                userId = accessKeyUser.getAccessKeyString();
            } else if (principal instanceof com.project.fnb.infrastructure.security.AppUserPrincipal appUser) {
                userId = appUser.getFullName() != null ? appUser.getFullName() + " (" + appUser.getUsername() + ")" : appUser.getUsername();
                userType = "OWNER";
            } else if (principal instanceof Jwt jwt) {
                userId = jwt.getClaimAsString("preferred_username") != null ? jwt.getClaimAsString("preferred_username") : jwt.getSubject();
                userType = "OWNER";
            } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                userId = userDetails.getUsername();
                userType = "OWNER";
            } else if (principal instanceof String) {
                userId = (String) principal;
            }
        }

        PosActionAudit audit = PosActionAudit.builder()
                .tenantId(TenantContext.getTenantId())
                .userId(userId)
                .userType(userType)
                .accessKeyId(accessKeyId)
                .accessKeyRole(accessKeyRole)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .sessionId(sessionId)
                .amount(amount)
                .note(note)
                .build();

        repository.save(audit);
    }

    @Transactional
    public void record(String action, String targetType, String targetId, BigDecimal amount, String note) {
        Long sId = null;
        if ("SESSION".equals(targetType)) {
            try {
                sId = targetId != null ? Long.valueOf(targetId) : null;
            } catch (NumberFormatException ignored) {
            }
        }
        record(action, targetType, targetId, amount, note, sId);
    }

    public org.springframework.data.domain.Page<PosActionAudit> queryAudit(String tenantId, String action, String userId, String accessKeyId, String targetType, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        return repository.search(tenantId, action, userId, accessKeyId, targetType, pageable);
    }
}

