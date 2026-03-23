package com.project.fnb.modules.global.repository;

import com.project.fnb.modules.global.entity.AccessKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessKeyRepository extends JpaRepository<AccessKey, String> {
    Optional<AccessKey> findByKeyStringAndIsActiveTrue(String keyString);
    List<AccessKey> findByTenantId(String tenantId);
}
