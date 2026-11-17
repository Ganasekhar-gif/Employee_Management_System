package com.company.expense.dto.request;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record FinanceReportFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        Long departmentId,
        Long categoryId,
        Long employeeId) {
}
