package com.project.fnb.modules.global.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.modules.global.dto.AccessKeyDto;
import com.project.fnb.modules.global.dto.CreateAccessKeyRequest;
import com.project.fnb.modules.global.entity.AccessKey;
import com.project.fnb.modules.global.entity.AccessRole;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.AccessKeyRepository;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.global.service.impl.AccessKeyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccessKeyServiceTest {

    @Mock
    private AccessKeyRepository accessKeyRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private AccessKeyServiceImpl accessKeyService;

    private Tenant tenant;
    private CreateAccessKeyRequest request;
    private AccessKey accessKey;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId("tenant-1");
        tenant.setOwnerId("user-1");

        request = new CreateAccessKeyRequest();
        request.setName("Test Key");
        request.setRole(AccessRole.WAITER);

        accessKey = new AccessKey();
        accessKey.setId("key-1");
        accessKey.setTenantId("tenant-1");
        accessKey.setKeyString("random-string");
        accessKey.setName("Test Key");
        accessKey.setRole(AccessRole.WAITER);
        accessKey.setIsActive(true);
    }

    @Test
    void createKey_Success() {
        when(tenantRepository.findById("tenant-1")).thenReturn(Optional.of(tenant));
        when(accessKeyRepository.save(any(AccessKey.class))).thenReturn(accessKey);

        AccessKeyDto result = accessKeyService.createKey("tenant-1", "user-1", request);

        assertNotNull(result);
        assertEquals("Test Key", result.getName());
        assertEquals("random-string", result.getKeyString());
        assertEquals(AccessRole.WAITER, result.getRole());
        assertTrue(result.getIsActive());

        verify(accessKeyRepository, times(1)).save(any(AccessKey.class));
    }

    @Test
    void createKey_NotOwner_ThrowsAppException() {
        when(tenantRepository.findById("tenant-1")).thenReturn(Optional.of(tenant));

        AppException exception = assertThrows(AppException.class, () -> {
            accessKeyService.createKey("tenant-1", "user-2", request);
        });

        assertEquals(403, exception.getErrorCode());
        verify(accessKeyRepository, never()).save(any(AccessKey.class));
    }

    @Test
    void getKeysByTenant_Success() {
        when(tenantRepository.findById("tenant-1")).thenReturn(Optional.of(tenant));
        when(accessKeyRepository.findByTenantId("tenant-1")).thenReturn(List.of(accessKey));

        List<AccessKeyDto> results = accessKeyService.getKeysByTenant("tenant-1", "user-1");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("key-1", results.get(0).getId());
    }

    @Test
    void revokeKey_Success() {
        when(tenantRepository.findById("tenant-1")).thenReturn(Optional.of(tenant));
        when(accessKeyRepository.findById("key-1")).thenReturn(Optional.of(accessKey));

        accessKeyService.revokeKey("tenant-1", "key-1", "user-1");

        verify(accessKeyRepository, times(1)).delete(accessKey);
        verify(accessKeyRepository, never()).save(any(AccessKey.class));
    }

    @Test
    void revokeKey_KeyNotBelongToTenant_ThrowsAppException() {
        when(tenantRepository.findById("tenant-1")).thenReturn(Optional.of(tenant));
        accessKey.setTenantId("tenant-2");
        when(accessKeyRepository.findById("key-1")).thenReturn(Optional.of(accessKey));

        AppException exception = assertThrows(AppException.class, () -> {
            accessKeyService.revokeKey("tenant-1", "key-1", "user-1");
        });

        assertEquals(400, exception.getErrorCode());
        verify(accessKeyRepository, never()).save(any(AccessKey.class));
    }
}
