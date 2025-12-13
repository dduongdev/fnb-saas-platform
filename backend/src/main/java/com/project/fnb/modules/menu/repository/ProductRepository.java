package com.project.fnb.modules.menu.repository;

import com.project.fnb.modules.menu.entity.Product;

import java.util.Optional;

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
}