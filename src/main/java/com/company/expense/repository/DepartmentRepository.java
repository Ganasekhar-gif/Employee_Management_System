package com.company.expense.repository;

import com.company.expense.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    boolean existsByCode(String code);

    boolean existsByName(String name);
}
