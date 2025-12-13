package com.project.fnb.modules.global.repository;

import com.project.fnb.modules.global.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, String> {
    Page<Tenant> findByIsActiveTrue(Pageable pageable);
}