package com.project.fnb.modules.menu.repository;

import com.project.fnb.modules.menu.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    List<Category> findByIsActiveTrueOrderByDisplayOrderAsc();

    Optional<Category> findByIsDefaultTrue();
}