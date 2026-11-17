package com.company.expense.dto.response;

import com.company.expense.entity.Expense;
import com.company.expense.entity.ExpenseApprovalStep;
import com.company.expense.enums.ApproverType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ApprovalTaskResponse(
        Long expenseId,
        int submissionNo,
        int stepOrder,
        ApproverType approverType,
        Long employeeId,
        String employeeName,
        String categoryName,
        BigDecimal amount,
        String currencyCode,
        LocalDate expenseDate,
        String description,
        Instant submittedAt) {

    public static ApprovalTaskResponse from(ExpenseApprovalStep step) {
        Expense expense = step.getExpense();
        return new ApprovalTaskResponse(
                expense.getId(),
                step.getSubmissionNo(),
                step.getStepOrder(),
                step.getApproverType(),
                expense.getEmployee().getId(),
                expense.getEmployee().getFullName(),
                expense.getCategory().getName(),
                expense.getAmount(),
                expense.getCurrencyCode(),
                expense.getExpenseDate(),
                expense.getDescription(),
                expense.getSubmittedAt());
    }
}
