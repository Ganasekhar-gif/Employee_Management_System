package com.company.expense.repository;

import com.company.expense.entity.ExpenseCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    boolean existsByName(String name);

    List<ExpenseCategory> findByActiveTrueOrderByName();
}
