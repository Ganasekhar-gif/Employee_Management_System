package com.company.expense.service;

import com.company.expense.dto.request.FinanceReportFilter;
import com.company.expense.dto.response.FinanceReportResponse;

public interface FinanceReportService {

    FinanceReportResponse findApprovedExpenses(FinanceReportFilter filter);

    String exportApprovedExpensesCsv(Long actorId, FinanceReportFilter filter);
}
