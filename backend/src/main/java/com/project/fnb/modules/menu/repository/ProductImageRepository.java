package com.project.fnb.modules.menu.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.project.fnb.modules.menu.entity.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    @Override
    @NonNull
    @Query("SELECT p FROM ProductImage p WHERE p.id = :id")
    Optional<ProductImage> findById(@NonNull @Param("id") Long id);
}
