package com.project.fnb.modules.menu.repository;

import com.project.fnb.modules.menu.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    List<Category> findByIsActiveTrueOrderByDisplayOrderAsc();

    Optional<Category> findByIsDefaultTrue();

    @Override
    @NonNull
    @Query("SELECT c FROM Category c WHERE c.id = :id")
    Optional<Category> findById(@NonNull @Param("id") Integer id);

    @Query("SELECT DISTINCT c FROM Category c " +
           "LEFT JOIN FETCH c.products p " +
           "WHERE c.isActive = true " +
           "ORDER BY c.displayOrder ASC")
    List<Category> findAllWithProducts();

    /**
     * Lấy tất cả categories với products và images (eager load).
     * 
     * <p><b>Purpose:</b> Fix N+1 query issue trong MenuService.getPublicMenu().
     * Đầy đủ JOIN FETCH chain: categories → products → images.</p>
     * 
     * <p><b>Performance:</b> 1 query thay vì 1 + N + M queries.</p>
     * 
     * @return Danh sách Category với tất cả products + images
     */
    @Query("SELECT DISTINCT c FROM Category c " +
           "LEFT JOIN FETCH c.products p " +
           "LEFT JOIN FETCH p.images " +
           "WHERE c.isActive = true " +
           "ORDER BY c.displayOrder ASC")
    List<Category> findAllWithProductsAndImages();
}