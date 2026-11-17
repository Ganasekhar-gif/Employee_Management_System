package com.company.expense.service.impl;

import com.company.expense.dto.request.FinanceReportFilter;
import com.company.expense.dto.response.FinanceReportResponse;
import com.company.expense.entity.Expense;
import com.company.expense.entity.User;
import com.company.expense.enums.AuditAction;
import com.company.expense.enums.AuditEntityType;
import com.company.expense.exception.BusinessRuleException;
import com.company.expense.exception.ResourceNotFoundException;
import com.company.expense.repository.ExpenseRepository;
import com.company.expense.repository.ExpenseSpecifications;
import com.company.expense.repository.UserRepository;
import com.company.expense.service.AuditLogService;
import com.company.expense.service.FinanceReportService;
import com.company.expense.util.CsvWriter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FinanceReportServiceImpl implements FinanceReportService {

    private static final Sort REPORT_ORDER = Sort.by("expenseDate", "id");
    private static final List<String> CSV_HEADER = List.of(
            "expense_id", "employee", "department", "category", "expense_date", "amount", "currency", "status",
            "submitted_at");

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public FinanceReportServiceImpl(
            ExpenseRepository expenseRepository, UserRepository userRepository, AuditLogService auditLogService) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    public FinanceReportResponse findApprovedExpenses(FinanceReportFilter filter) {
        return FinanceReportResponse.from(findMatching(filter));
    }

    @Override
    @Transactional
    public String exportApprovedExpensesCsv(Long actorId, FinanceReportFilter filter) {
        FinanceReportResponse report = FinanceReportResponse.from(findMatching(filter));

        CsvWriter csv = new CsvWriter().row(CSV_HEADER);
        report.expenses().forEach(row -> csv.row(Arrays.asList(
                row.expenseId(), row.employee(), row.department(), row.category(), row.expenseDate(),
                row.amount(), row.currencyCode(), row.status(), row.submittedAt())));

        auditLogService.record(findUser(actorId), AuditEntityType.EXPENSE_REPORT, null, AuditAction.EXPORT,
                describe(filter, report.count()));
        return csv.toString();
    }

    private List<Expense> findMatching(FinanceReportFilter filter) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BusinessRuleException("'from' must be on or before 'to'");
        }
        return expenseRepository.findAll(ExpenseSpecifications.approvedMatching(filter), REPORT_ORDER);
    }

    private static Map<String, Object> describe(FinanceReportFilter filter, int rowCount) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rowCount", rowCount);
        if (filter.from() != null) {
            details.put("from", filter.from().toString());
        }
        if (filter.to() != null) {
            details.put("to", filter.to().toString());
        }
        if (filter.departmentId() != null) {
            details.put("departmentId", filter.departmentId());
        }
        if (filter.categoryId() != null) {
            details.put("categoryId", filter.categoryId());
        }
        if (filter.employeeId() != null) {
            details.put("employeeId", filter.employeeId());
        }
        return details;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
