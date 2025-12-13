package com.project.fnb.modules.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.fnb.modules.menu.entity.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    
}
