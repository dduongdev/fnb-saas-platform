package com.project.fnb.modules.hrm.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.project.fnb.modules.hrm.entity.Shift;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
    List<Shift> findByStartTimeBetween(LocalDateTime from, LocalDateTime to);

    @Override
    @NonNull
    @Query("SELECT s FROM Shift s WHERE s.id = :id")
    Optional<Shift> findById(@NonNull @Param("id") Long id);
}
