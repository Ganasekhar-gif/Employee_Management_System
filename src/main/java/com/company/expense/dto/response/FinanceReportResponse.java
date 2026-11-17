package com.company.expense.dto.response;

import com.company.expense.entity.Department;
import com.company.expense.entity.Expense;
import com.company.expense.enums.ExpenseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record FinanceReportResponse(int count, BigDecimal totalAmount, String currencyCode, List<Row> expenses) {

    public static FinanceReportResponse from(List<Expense> expenses) {
        List<Row> rows = expenses.stream().map(Row::from).toList();
        BigDecimal total = rows.stream().map(Row::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinanceReportResponse(rows.size(), total, Expense.CURRENCY_CODE, rows);
    }

    public record Row(
            Long expenseId,
            String employee,
            String department,
            String category,
            LocalDate expenseDate,
            BigDecimal amount,
            String currencyCode,
            ExpenseStatus status,
            Instant submittedAt) {

        static Row from(Expense expense) {
            Department department = expense.getEmployee().getDepartment();
            return new Row(
                    expense.getId(),
                    expense.getEmployee().getFullName(),
                    department == null ? null : department.getName(),
                    expense.getCategory().getName(),
                    expense.getExpenseDate(),
                    expense.getAmount(),
                    expense.getCurrencyCode(),
                    expense.getStatus(),
                    expense.getSubmittedAt());
        }
    }
}
