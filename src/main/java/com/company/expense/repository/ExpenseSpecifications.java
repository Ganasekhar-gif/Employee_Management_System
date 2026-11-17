package com.company.expense.repository;

import com.company.expense.dto.request.FinanceReportFilter;
import com.company.expense.entity.Expense;
import com.company.expense.enums.ExpenseStatus;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

public final class ExpenseSpecifications {

    private ExpenseSpecifications() {
    }

    public static Specification<Expense> approvedMatching(FinanceReportFilter filter) {
        Specification<Expense> specification = hasStatus(ExpenseStatus.APPROVED);
        if (filter.from() != null) {
            specification = specification.and(expenseDateOnOrAfter(filter.from()));
        }
        if (filter.to() != null) {
            specification = specification.and(expenseDateOnOrBefore(filter.to()));
        }
        if (filter.departmentId() != null) {
            specification = specification.and(inDepartment(filter.departmentId()));
        }
        if (filter.categoryId() != null) {
            specification = specification.and(inCategory(filter.categoryId()));
        }
        if (filter.employeeId() != null) {
            specification = specification.and(submittedBy(filter.employeeId()));
        }
        return specification;
    }

    static Specification<Expense> hasStatus(ExpenseStatus status) {
        return (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    static Specification<Expense> expenseDateOnOrAfter(LocalDate from) {
        return (root, query, builder) -> builder.greaterThanOrEqualTo(root.get("expenseDate"), from);
    }

    static Specification<Expense> expenseDateOnOrBefore(LocalDate to) {
        return (root, query, builder) -> builder.lessThanOrEqualTo(root.get("expenseDate"), to);
    }

    static Specification<Expense> inDepartment(Long departmentId) {
        return (root, query, builder) ->
                builder.equal(root.get("employee").get("department").get("id"), departmentId);
    }

    static Specification<Expense> inCategory(Long categoryId) {
        return (root, query, builder) -> builder.equal(root.get("category").get("id"), categoryId);
    }

    static Specification<Expense> submittedBy(Long employeeId) {
        return (root, query, builder) -> builder.equal(root.get("employee").get("id"), employeeId);
    }
}
