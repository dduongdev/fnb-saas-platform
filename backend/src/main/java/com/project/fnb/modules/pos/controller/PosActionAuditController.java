package com.project.fnb.modules.pos.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.pos.entity.PosActionAudit;
import com.project.fnb.modules.pos.service.PosActionAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pos/audit")
@RequiredArgsConstructor
public class PosActionAuditController {

    private final PosActionAuditService auditService;

    @GetMapping
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<Page<PosActionAudit>> getAuditEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String accessKeyId,
            @RequestParam(required = false) String targetType
    ) {
        String tenantId = com.project.fnb.infrastructure.security.TenantContext.getTenantId();
        Page<PosActionAudit> result = auditService.queryAudit(tenantId, action, userId, accessKeyId, targetType, page, size);
        return ApiResponse.success(result);
    }
}
