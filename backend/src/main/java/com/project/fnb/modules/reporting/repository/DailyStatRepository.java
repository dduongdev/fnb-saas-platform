package com.project.fnb.modules.reporting.repository;

import com.project.fnb.modules.reporting.entity.DailyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyStatRepository extends JpaRepository<DailyStat, Long> {
    
    Optional<DailyStat> findByReportDate(LocalDate reportDate);

    List<DailyStat> findByReportDateBetweenOrderByReportDateAsc(LocalDate startDate, LocalDate endDate);

    @Override
    @NonNull
    @Query("SELECT d FROM DailyStat d WHERE d.id = :id")
    Optional<DailyStat> findById(@NonNull @Param("id") Long id);
}