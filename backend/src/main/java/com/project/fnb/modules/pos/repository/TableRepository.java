package com.project.fnb.modules.pos.repository;

import com.project.fnb.modules.pos.entity.DiningTable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho quản lý DiningTable entities.
 * 
 * <p><b>Simple CRUD:</b> Không có custom queries phức tạp, chỉ override để đảm bảo tenant isolation.</p>
 * 
 * @see DiningTable
 */
public interface TableRepository extends JpaRepository<DiningTable, String> {
    
    /**
     * Lấy tất cả bàn với sort order.
     * 
     * <p><b>Common Usage:</b> {@code findAll(Sort.by("name"))} để lấy danh sách bàn sắp xếp theo tên.</p>
     * 
     * @param sort Sort order
     * @return Danh sách DiningTable
     */
    List<DiningTable> findAll(Sort sort);

    /**
     * Tìm bàn theo ID (tenant-scoped).
     * 
     * <p><b>Tenant Isolation:</b> Tự động filter theo tenantId qua Hibernate filter.</p>
     * 
     * @param id Table ID (UUID)
     * @return Optional&lt;DiningTable&gt;
     */
    @Override
    @NonNull
    @Query("SELECT t FROM DiningTable t WHERE t.id = :id")
    Optional<DiningTable> findById(@NonNull @Param("id") String id);

    /**
     * Lấy tất cả bàn với current sessions (eager load).
     * 
     * <p><b>Purpose:</b> Fix N+1 query issue trong SessionService.notifyTableUpdate().
     * Eager load currentSession để tránh N queries khi access t.getCurrentSession().</p>
     * 
     * @return List&lt;DiningTable&gt; với tất cả currentSession đã load
     */
    @Query("SELECT t FROM DiningTable t " +
           "LEFT JOIN FETCH t.currentSession " +
           "WHERE t.isDeleted = false")
    List<DiningTable> findAllWithSession();
}