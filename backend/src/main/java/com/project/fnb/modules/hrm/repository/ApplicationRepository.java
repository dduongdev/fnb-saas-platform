package com.project.fnb.modules.hrm.repository;

import com.project.fnb.modules.hrm.entity.Application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Integer> {
    Optional<Application> findByUserIdAndStatus(String userId, Application.Status status);
    boolean existsByUserIdAndJobPostId(String userId, Long jobPostId);

    @Override
    @NonNull
    @Query("SELECT a FROM Application a WHERE a.id = :id")
    Optional<Application> findById(@NonNull @Param("id") Integer id);
}