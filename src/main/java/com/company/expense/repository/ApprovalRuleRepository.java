package com.company.expense.repository;

import com.company.expense.entity.ApprovalRule;
import com.company.expense.entity.Department;
import com.company.expense.entity.ExpenseCategory;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, Long> {

    boolean existsByPriorityAndActiveTrue(int priority);

    List<ApprovalRule> findAllByOrderByPriorityAscIdAsc();

    @Query("""
            select r from ApprovalRule r
            where r.active = true
              and (r.category is null or r.category = :category)
              and (r.department is null or r.department = :department)
              and r.minAmount <= :amount
              and (r.maxAmount is null or r.maxAmount >= :amount)
            order by r.priority
            """)
    List<ApprovalRule> findMatchingActiveRules(
            @Param("category") ExpenseCategory category,
            @Param("department") Department department,
            @Param("amount") BigDecimal amount);
}
