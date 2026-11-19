package com.company.expense.controller;

import com.company.expense.dto.request.ApprovalRuleRequest;
import com.company.expense.dto.response.ApprovalRuleResponse;
import com.company.expense.security.AuthenticatedUser;
import com.company.expense.service.ApprovalRuleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/approval-rules")
public class AdminApprovalRuleController {

    private final ApprovalRuleService ruleService;

    public AdminApprovalRuleController(ApprovalRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalRuleResponse createRule(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ApprovalRuleRequest request) {
        return ruleService.createRule(principal.id(), request);
    }

    @GetMapping
    public List<ApprovalRuleResponse> listRules() {
        return ruleService.listRules();
    }

    @PutMapping("/{id}")
    public ApprovalRuleResponse updateRule(@AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id, @Valid @RequestBody ApprovalRuleRequest request) {
        return ruleService.updateRule(principal.id(), id, request);
    }

    @PostMapping("/{id}/activate")
    public ApprovalRuleResponse activateRule(@AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        return ruleService.activateRule(principal.id(), id);
    }

    @PostMapping("/{id}/deactivate")
    public ApprovalRuleResponse deactivateRule(@AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        return ruleService.deactivateRule(principal.id(), id);
    }
}
