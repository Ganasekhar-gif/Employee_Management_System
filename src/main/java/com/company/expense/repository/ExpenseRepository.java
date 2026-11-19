package com.company.expense.repository;

import com.company.expense.entity.Expense;
import com.company.expense.entity.ExpenseCategory;
import com.company.expense.entity.User;
import com.company.expense.enums.ExpenseStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    @EntityGraph(attributePaths = "category")
    List<Expense> findByEmployee_IdOrderByIdDesc(Long employeeId);

    @EntityGraph(attributePaths = "category")
    List<Expense> findByEmployee_IdAndStatusOrderByIdDesc(Long employeeId, ExpenseStatus status);

    // Row lock so two approvers cannot decide the same step concurrently.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Expense e where e.id = :id")
    Optional<Expense> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select e.id from Expense e
            where e.employee = :employee and e.category = :category
              and e.status in :statuses and (:excludedId is null or e.id <> :excludedId)
            order by e.id
            """)
    List<Long> findOtherIdsInCategory(@Param("employee") User employee, @Param("category") ExpenseCategory category,
            @Param("statuses") Collection<ExpenseStatus> statuses, @Param("excludedId") Long excludedId);

    @Override
    @EntityGraph(attributePaths = {"employee", "employee.department", "category"})
    List<Expense> findAll(Specification<Expense> specification, Sort sort);
}
