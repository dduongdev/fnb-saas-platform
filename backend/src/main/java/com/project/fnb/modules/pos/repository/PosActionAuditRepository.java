package com.project.fnb.modules.pos.repository;

import com.project.fnb.modules.pos.entity.PosActionAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PosActionAuditRepository extends JpaRepository<PosActionAudit, String> {

    @Query("SELECT p FROM PosActionAudit p " +
            "WHERE p.tenantId = :tenantId " +
            "AND (:action IS NULL OR LOWER(p.action) LIKE LOWER(CONCAT('%', :action, '%'))) " +
            "AND (:userId IS NULL OR p.userId = :userId) " +
            "AND (:accessKeyId IS NULL OR p.accessKeyId = :accessKeyId) " +
            "AND (:targetType IS NULL OR p.targetType = :targetType)")
    Page<PosActionAudit> search(
            @Param("tenantId") String tenantId,
            @Param("action") String action,
            @Param("userId") String userId,
            @Param("accessKeyId") String accessKeyId,
            @Param("targetType") String targetType,
            Pageable pageable
    );
}
