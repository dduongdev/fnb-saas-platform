package com.project.fnb.modules.global.repository;

import com.project.fnb.modules.global.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TenantRepository extends JpaRepository<Tenant, String> {
    Page<Tenant> findByIsActiveTrue(Pageable pageable);
    
    /**
     * Tìm tất cả tenant theo owner ID (query-based, không load all vào memory).
     * 
     * <p><b>Purpose:</b> Fix N+1 query issue trong TenantService.getMyTenants().
     * Dùng SQL WHERE clause thay vì findAll() + in-memory filter.</p>
     * 
     * @param ownerId User ID của owner
     * @return List<Tenant> các tenant sở hữu bởi owner này
     */
    @Query("SELECT t FROM Tenant t WHERE t.ownerId = :ownerId")
    List<Tenant> findByOwnerId(@Param("ownerId") String ownerId);
}