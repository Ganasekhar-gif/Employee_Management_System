package com.company.expense.entity;

import com.company.expense.enums.ApprovalStepStatus;
import com.company.expense.enums.ApproverType;
import com.company.expense.enums.RoleName;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "expense_approval_steps")
public class ExpenseApprovalStep extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id")
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private ApprovalRule rule;

    private int submissionNo;

    private int stepOrder;

    @Enumerated(EnumType.STRING)
    private ApproverType approverType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_approver_id")
    private User assignedApprover;

    @Enumerated(EnumType.STRING)
    private ApprovalStepStatus status = ApprovalStepStatus.PENDING;

    private String decisionComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    private Instant decidedAt;

    protected ExpenseApprovalStep() {
    }

    ExpenseApprovalStep(Expense expense, ApprovalRule rule, int submissionNo, int stepOrder,
            ApproverType approverType, User assignedApprover) {
        this.expense = expense;
        this.rule = rule;
        this.submissionNo = submissionNo;
        this.stepOrder = stepOrder;
        this.approverType = approverType;
        this.assignedApprover = assignedApprover;
    }

    public boolean isPending() {
        return status == ApprovalStepStatus.PENDING;
    }

    public boolean canBeDecidedBy(User actor) {
        if (!actor.isActive() || expense.isOwnedBy(actor)) {
            return false;
        }
        if (approverType == ApproverType.FINANCE_ROLE) {
            return actor.hasRole(RoleName.FINANCE);
        }
        return actor.equals(assignedApprover);
    }

    public boolean involves(User user) {
        return user.equals(assignedApprover) || user.equals(decidedBy);
    }

    void approve(User actor, String comment) {
        decide(ApprovalStepStatus.APPROVED, actor, comment);
    }

    void reject(User actor, String reason) {
        decide(ApprovalStepStatus.REJECTED, actor, reason);
    }

    void skip() {
        this.status = ApprovalStepStatus.SKIPPED;
    }

    private void decide(ApprovalStepStatus decision, User actor, String comment) {
        this.status = decision;
        this.decidedBy = actor;
        this.decisionComment = comment;
        this.decidedAt = Timestamps.now();
    }

    public Expense getExpense() {
        return expense;
    }

    public ApprovalRule getRule() {
        return rule;
    }

    public int getSubmissionNo() {
        return submissionNo;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public ApproverType getApproverType() {
        return approverType;
    }

    public User getAssignedApprover() {
        return assignedApprover;
    }

    public ApprovalStepStatus getStatus() {
        return status;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public User getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
