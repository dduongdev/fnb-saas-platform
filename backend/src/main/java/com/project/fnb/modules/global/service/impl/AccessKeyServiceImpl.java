package com.project.fnb.modules.global.service.impl;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.modules.global.dto.AccessKeyDto;
import com.project.fnb.modules.global.dto.CreateAccessKeyRequest;
import com.project.fnb.modules.global.entity.AccessKey;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.AccessKeyRepository;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.global.service.AccessKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccessKeyServiceImpl implements AccessKeyService {

    private final AccessKeyRepository accessKeyRepository;
    private final TenantRepository tenantRepository;

    private void validateOwner(String tenantId, String currentUserId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(404, "Tenant không tồn tại"));
        if (!tenant.getOwnerId().equals(currentUserId)) {
            throw new AppException(403, "Chỉ chủ quán mới có quyền quản lý khoá truy cập");
        }
    }

    @Override
    public AccessKeyDto createKey(String tenantId, String currentUserId, CreateAccessKeyRequest request) {
        validateOwner(tenantId, currentUserId);
        
        String keyString = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        AccessKey accessKey = AccessKey.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .keyString(keyString)
                .role(request.getRole())
                .isActive(true)
                .build();
                
        AccessKey savedKey = accessKeyRepository.save(accessKey);
        
        return AccessKeyDto.builder()
                .id(savedKey.getId())
                .name(savedKey.getName())
                .keyString(savedKey.getKeyString())
                .role(savedKey.getRole())
                .isActive(savedKey.getIsActive())
                .createdAt(savedKey.getCreatedAt())
                .build();
    }

    @Override
    public List<AccessKeyDto> getKeysByTenant(String tenantId, String currentUserId) {
        validateOwner(tenantId, currentUserId);
        
        return accessKeyRepository.findByTenantId(tenantId).stream()
                .map(key -> AccessKeyDto.builder()
                        .id(key.getId())
                        .name(key.getName())
                        .keyString(key.getKeyString())
                        .role(key.getRole())
                        .isActive(key.getIsActive())
                        .createdAt(key.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void revokeKey(String tenantId, String keyId, String currentUserId) {
        validateOwner(tenantId, currentUserId);
        
        AccessKey accessKey = accessKeyRepository.findById(keyId)
                .orElseThrow(() -> new AppException(404, "Khoá không tồn tại"));
                
        if (!accessKey.getTenantId().equals(tenantId)) {
            throw new AppException(400, "Khoá không thuộc Tenant này");
        }
        
        accessKey.setIsActive(false);
        accessKeyRepository.save(accessKey);
    }
}
