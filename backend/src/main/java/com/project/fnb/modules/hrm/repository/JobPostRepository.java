package com.project.fnb.modules.hrm.repository;

import com.project.fnb.modules.hrm.entity.JobPost;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

public interface JobPostRepository extends JpaRepository<JobPost, Long> {
    Page<JobPost> findAllByOrderByCreatedAtDesc(Pageable pageable);

     /**
     * Lấy Tenant ID của một Job Post bất kỳ.
     * Sử dụng nativeQuery = true để:
     * 1. Bỏ qua TenantFilter (vì ta chưa biết tenant nào để filter).
     * 2. Tối ưu hiệu suất (chỉ select 1 cột thay vì load cả Entity).
     */
    @Query(value = "SELECT tenant_id FROM job_posts WHERE id = :id AND is_deleted = false", nativeQuery = true)
    Optional<String> findTenantIdById(@Param("id") Long id);

    /**
     * Ghi đè findById mặc định.
     * Sử dụng @Query JPQL để ép Hibernate coi đây là một câu Query thay vì Direct Load.
     * Nhờ đó, @Filter(tenantFilter) sẽ được áp dụng vào mệnh đề WHERE.
     */
    @Override
    @NonNull
    @Query("SELECT j FROM JobPost j WHERE j.id = :id")
    Optional<JobPost> findById(@NonNull @Param("id") Long id);

    @Query(value = "SELECT * FROM job_posts WHERE is_active = true AND is_deleted = false ORDER BY created_at DESC", 
           countQuery = "SELECT count(*) FROM job_posts WHERE is_active = true AND is_deleted = false",
           nativeQuery = true)
    Page<JobPost> findAllPublicJobs(Pageable pageable);
}