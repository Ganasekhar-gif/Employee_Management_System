package com.company.expense.controller;

import com.company.expense.dto.request.FinanceReportFilter;
import com.company.expense.dto.response.FinanceLookupsResponse;
import com.company.expense.dto.response.FinanceReportResponse;
import com.company.expense.security.AuthenticatedUser;
import com.company.expense.service.FinanceReportService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/expenses")
public class FinanceReportController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final FinanceReportService reportService;

    public FinanceReportController(FinanceReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/filters")
    public FinanceLookupsResponse filterOptions() {
        return reportService.filterOptions();
    }

    @GetMapping
    public FinanceReportResponse findApprovedExpenses(@ModelAttribute FinanceReportFilter filter) {
        return reportService.findApprovedExpenses(filter);
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportApprovedExpenses(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @ModelAttribute FinanceReportFilter filter) {
        String csv = reportService.exportApprovedExpensesCsv(principal.id(), filter);
        ContentDisposition disposition = ContentDisposition.attachment().filename("approved-expenses.csv").build();
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(csv);
    }
}
