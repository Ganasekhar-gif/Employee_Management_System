package com.company.expense.service.impl;

import com.company.expense.dto.response.ApprovalTaskResponse;
import com.company.expense.dto.response.ExpenseResponse;
import com.company.expense.entity.Expense;
import com.company.expense.entity.ExpenseApprovalStep;
import com.company.expense.entity.User;
import com.company.expense.enums.AuditAction;
import com.company.expense.enums.AuditEntityType;
import com.company.expense.exception.ResourceNotFoundException;
import com.company.expense.repository.ExpenseApprovalStepRepository;
import com.company.expense.repository.ExpenseRepository;
import com.company.expense.repository.UserRepository;
import com.company.expense.service.ApprovalService;
import com.company.expense.service.AuditLogService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ApprovalServiceImpl implements ApprovalService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseApprovalStepRepository stepRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public ApprovalServiceImpl(
            ExpenseRepository expenseRepository,
            ExpenseApprovalStepRepository stepRepository,
            UserRepository userRepository,
            AuditLogService auditLogService) {
        this.expenseRepository = expenseRepository;
        this.stepRepository = stepRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalTaskResponse> listPendingApprovals(Long actorId) {
        return stepRepository.findActionableFor(findUser(actorId)).stream()
                .map(ApprovalTaskResponse::from)
                .toList();
    }

    @Override
    public ExpenseResponse approve(Long actorId, Long expenseId, String comment) {
        User actor = findUser(actorId);
        Expense expense = findDecidableExpense(actor, expenseId);
        ExpenseApprovalStep step = expense.approve(actor, comment);
        recordDecision(actor, expense, step, AuditAction.APPROVE);
        return ExpenseResponse.from(expense);
    }

    @Override
    public ExpenseResponse reject(Long actorId, Long expenseId, String reason) {
        User actor = findUser(actorId);
        Expense expense = findDecidableExpense(actor, expenseId);
        ExpenseApprovalStep step = expense.reject(actor, reason);
        recordDecision(actor, expense, step, AuditAction.REJECT);
        return ExpenseResponse.from(expense);
    }

    // Users who may not see the expense get 404; users who can see it but are not the current approver get 403.
    private Expense findDecidableExpense(User actor, Long expenseId) {
        return expenseRepository.findByIdForUpdate(expenseId)
                .filter(expense -> expense.canBeViewedBy(actor))
                .orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));
    }

    private void recordDecision(User actor, Expense expense, ExpenseApprovalStep step, AuditAction action) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("submissionNo", step.getSubmissionNo());
        details.put("stepOrder", step.getStepOrder());
        details.put("expenseStatus", expense.getStatus().name());
        if (step.getDecisionComment() != null) {
            details.put("comment", step.getDecisionComment());
        }
        auditLogService.record(actor, AuditEntityType.EXPENSE, expense.getId(), action, details);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
