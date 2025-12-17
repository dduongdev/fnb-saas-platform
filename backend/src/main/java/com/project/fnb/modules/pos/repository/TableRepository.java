package com.project.fnb.modules.pos.repository;

import com.project.fnb.modules.pos.entity.DiningTable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface TableRepository extends JpaRepository<DiningTable, Integer> {
    
    // Tìm bàn con (Slave) của một bàn Master
    List<DiningTable> findByMasterTableId(Integer masterId);

    // Lấy toàn bộ bàn (Filter tự động chạy)
    List<DiningTable> findAll(Sort sort);

    @Override
    @NonNull
    @Query("SELECT t FROM DiningTable t WHERE t.id = :id")
    Optional<DiningTable> findById(@NonNull @Param("id") Integer id);
}