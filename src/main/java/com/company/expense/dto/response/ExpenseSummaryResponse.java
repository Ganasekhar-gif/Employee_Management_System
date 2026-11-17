package com.company.expense.dto.response;

import com.company.expense.entity.Expense;
import com.company.expense.enums.ExpenseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExpenseSummaryResponse(
        Long id,
        String categoryName,
        BigDecimal amount,
        String currencyCode,
        LocalDate expenseDate,
        String description,
        ExpenseStatus status,
        Instant submittedAt) {

    public static ExpenseSummaryResponse from(Expense expense) {
        return new ExpenseSummaryResponse(
                expense.getId(),
                expense.getCategory().getName(),
                expense.getAmount(),
                expense.getCurrencyCode(),
                expense.getExpenseDate(),
                expense.getDescription(),
                expense.getStatus(),
                expense.getSubmittedAt());
    }
}
