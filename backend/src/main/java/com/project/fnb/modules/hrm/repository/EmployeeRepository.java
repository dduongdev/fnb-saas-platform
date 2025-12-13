package com.project.fnb.modules.hrm.repository;

import com.project.fnb.modules.hrm.entity.Employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Integer> {
    Optional<Employee> findByUserIdAndTenantId(String userId, String tenantId);

    @Override
    @NonNull
    @Query("SELECT e FROM Employee e WHERE e.id = :id")
    Optional<Employee> findById(@NonNull @Param("id") Integer id);
}