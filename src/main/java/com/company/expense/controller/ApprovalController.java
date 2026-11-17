package com.company.expense.controller;

import com.company.expense.dto.request.ApprovalDecisionRequest;
import com.company.expense.dto.request.RejectionRequest;
import com.company.expense.dto.response.ApprovalTaskResponse;
import com.company.expense.dto.response.ExpenseResponse;
import com.company.expense.security.AuthenticatedUser;
import com.company.expense.service.ApprovalService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    public List<ApprovalTaskResponse> listPendingApprovals(@AuthenticationPrincipal AuthenticatedUser principal) {
        return approvalService.listPendingApprovals(principal.id());
    }

    @PostMapping("/{expenseId}/approve")
    public ExpenseResponse approve(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long expenseId,
            @Valid @RequestBody(required = false) ApprovalDecisionRequest request) {
        String comment = request == null ? null : request.comment();
        return approvalService.approve(principal.id(), expenseId, comment);
    }

    @PostMapping("/{expenseId}/reject")
    public ExpenseResponse reject(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long expenseId,
            @Valid @RequestBody RejectionRequest request) {
        return approvalService.reject(principal.id(), expenseId, request.reason());
    }
}
