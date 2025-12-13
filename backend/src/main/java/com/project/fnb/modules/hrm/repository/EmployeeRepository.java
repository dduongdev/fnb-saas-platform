package com.project.fnb.modules.hrm.repository;

import com.project.fnb.modules.hrm.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Integer> {
    Optional<Employee> findByUserIdAndTenantId(String userId, String tenantId);
}