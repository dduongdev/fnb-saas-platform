package com.project.fnb.modules.global.service;

import com.project.fnb.modules.global.dto.AccessKeyDto;
import com.project.fnb.modules.global.dto.CreateAccessKeyRequest;

import java.util.List;

public interface AccessKeyService {
    AccessKeyDto createKey(String tenantId, String currentUserId, CreateAccessKeyRequest request);
    List<AccessKeyDto> getKeysByTenant(String tenantId, String currentUserId);
    void revokeKey(String tenantId, String keyId, String currentUserId);
    List<String> getAccessRoles();
}
