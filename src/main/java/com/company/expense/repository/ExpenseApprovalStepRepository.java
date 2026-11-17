package com.company.expense.repository;

import com.company.expense.entity.ExpenseApprovalStep;
import com.company.expense.entity.User;
import com.company.expense.enums.ApprovalStepStatus;
import com.company.expense.enums.ApproverType;
import com.company.expense.enums.ExpenseStatus;
import com.company.expense.enums.RoleName;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseApprovalStepRepository extends JpaRepository<ExpenseApprovalStep, Long> {

    default List<ExpenseApprovalStep> findActionableFor(User approver) {
        return findActionable(approver, approver.hasRole(RoleName.FINANCE),
                ApprovalStepStatus.PENDING, ExpenseStatus.SUBMITTED, ApproverType.FINANCE_ROLE);
    }

    // A step is actionable when no earlier step of the same submission is still pending.
    @Query("""
            select s from ExpenseApprovalStep s
            join fetch s.expense e
            join fetch e.employee
            join fetch e.category
            where s.status = :pending
              and e.status = :submitted
              and e.employee <> :approver
              and (s.assignedApprover = :approver
                   or (s.approverType = :financeRole and :isFinance = true))
              and not exists (
                  select 1 from ExpenseApprovalStep earlier
                  where earlier.expense = e
                    and earlier.submissionNo = s.submissionNo
                    and earlier.status = :pending
                    and earlier.stepOrder < s.stepOrder)
            order by e.submittedAt, e.id
            """)
    List<ExpenseApprovalStep> findActionable(
            @Param("approver") User approver,
            @Param("isFinance") boolean isFinance,
            @Param("pending") ApprovalStepStatus pending,
            @Param("submitted") ExpenseStatus submitted,
            @Param("financeRole") ApproverType financeRole);
}
