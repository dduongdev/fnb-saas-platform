package com.project.fnb.modules.menu.repository;

import com.project.fnb.modules.menu.entity.Product;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Modifying
    @Query("UPDATE Product p SET p.category.id = :newCategoryId WHERE p.category.id = :oldCategoryId")
    void moveProductsToCategory(Integer oldCategoryId, Integer newCategoryId);

    @Override
    @NonNull
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findById(@NonNull @Param("id") Long id);

        @Query(value = "SELECT DISTINCT p FROM Product p " +
            "LEFT JOIN FETCH p.images " +
            "WHERE p.category.id = :categoryId AND p.isDeleted = false",
            countQuery = "SELECT COUNT(p) FROM Product p WHERE p.category.id = :categoryId AND p.isDeleted = false")
        Page<Product> findByCategoryIdWithImages(@Param("categoryId") Integer categoryId, Pageable pageable);

        @Query(value = "SELECT DISTINCT p FROM Product p " +
            "LEFT JOIN FETCH p.images " +
            "WHERE p.status = :status AND p.isDeleted = false",
            countQuery = "SELECT COUNT(p) FROM Product p WHERE p.status = :status AND p.isDeleted = false")
        Page<Product> findByStatusWithImages(@Param("status") Product.ProductStatus status, Pageable pageable);

        @Query(value = "SELECT DISTINCT p FROM Product p " +
            "LEFT JOIN FETCH p.images " +
            "WHERE p.category.id = :categoryId AND p.status = :status AND p.isDeleted = false",
            countQuery = "SELECT COUNT(p) FROM Product p WHERE p.category.id = :categoryId AND p.status = :status AND p.isDeleted = false")
        Page<Product> findByCategoryIdAndStatusWithImages(@Param("categoryId") Integer categoryId,
                                     @Param("status") Product.ProductStatus status,
                                     Pageable pageable);

    /**
     * Lấy tất cả products với images (eager load) - có pagination.
     * 
     * <p><b>Purpose:</b> Fix N+1 query issue trong ProductService.getAllProducts().
     * Eager load tất cả images trong 1 query thay vì N queries.</p>
     * 
     * @param pageable Pagination
     * @return Page&lt;Product&gt; với tất cả images đã load
     */
        @Query(value = "SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN FETCH p.images " +
            "WHERE p.isDeleted = false",
            countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isDeleted = false")
    Page<Product> findAllWithImages(Pageable pageable);

        @Query("SELECT p FROM Product p LEFT JOIN FETCH p.images WHERE p.id = :id")
        Optional<Product> findByIdWithImages(@Param("id") Long id);
}