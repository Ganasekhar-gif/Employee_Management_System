package com.company.expense.dto.response;

import com.company.expense.entity.Expense;
import com.company.expense.entity.ExpenseApprovalStep;
import com.company.expense.enums.ApprovalStepStatus;
import com.company.expense.enums.ApproverType;
import com.company.expense.enums.ExpenseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DecisionHistoryResponse(
        Long expenseId,
        int submissionNo,
        int stepOrder,
        ApproverType approverType,
        ApprovalStepStatus decision,
        String comment,
        Instant decidedAt,
        String employeeName,
        String categoryName,
        BigDecimal amount,
        String currencyCode,
        LocalDate expenseDate,
        ExpenseStatus expenseStatus) {

    public static DecisionHistoryResponse from(ExpenseApprovalStep step) {
        Expense expense = step.getExpense();
        return new DecisionHistoryResponse(
                expense.getId(),
                step.getSubmissionNo(),
                step.getStepOrder(),
                step.getApproverType(),
                step.getStatus(),
                step.getDecisionComment(),
                step.getDecidedAt(),
                expense.getEmployee().getFullName(),
                expense.getCategory().getName(),
                expense.getAmount(),
                expense.getCurrencyCode(),
                expense.getExpenseDate(),
                expense.getStatus());
    }
}
