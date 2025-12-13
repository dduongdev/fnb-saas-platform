package com.project.fnb.modules.hrm.repository;

import com.project.fnb.modules.hrm.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Integer> {
    Optional<Application> findByUserIdAndStatus(String userId, Application.Status status);
}