package com.company.expense.entity;

import com.company.expense.enums.ApproverType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "approval_rule_steps")
public class ApprovalRuleStep extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id")
    private ApprovalRule rule;

    private int stepOrder;

    @Enumerated(EnumType.STRING)
    private ApproverType approverType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_user_id")
    private User approverUser;

    protected ApprovalRuleStep() {
    }

    ApprovalRuleStep(ApprovalRule rule, int stepOrder, ApproverType approverType, User approverUser) {
        this.rule = rule;
        this.stepOrder = stepOrder;
        this.approverType = approverType;
        this.approverUser = approverUser;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public ApproverType getApproverType() {
        return approverType;
    }

    public User getApproverUser() {
        return approverUser;
    }
}
