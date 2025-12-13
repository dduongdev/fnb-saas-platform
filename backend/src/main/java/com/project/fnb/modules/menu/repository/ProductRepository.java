package com.project.fnb.modules.menu.repository;

import com.project.fnb.modules.menu.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Modifying
    @Query("UPDATE Product p SET p.category.id = :newCategoryId WHERE p.category.id = :oldCategoryId")
    void moveProductsToCategory(Integer oldCategoryId, Integer newCategoryId);
}