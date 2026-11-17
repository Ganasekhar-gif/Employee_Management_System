package com.company.expense.dto.response;

import static com.company.expense.dto.response.EntityReferences.idOf;

import com.company.expense.entity.ApprovalRule;
import com.company.expense.entity.ApprovalRuleStep;
import com.company.expense.enums.ApproverType;
import java.math.BigDecimal;
import java.util.List;

public record ApprovalRuleResponse(
        Long id,
        String name,
        Long categoryId,
        Long departmentId,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        int priority,
        boolean active,
        List<Step> steps) {

    public static ApprovalRuleResponse from(ApprovalRule rule) {
        return new ApprovalRuleResponse(
                rule.getId(),
                rule.getName(),
                idOf(rule.getCategory()),
                idOf(rule.getDepartment()),
                rule.getMinAmount(),
                rule.getMaxAmount(),
                rule.getPriority(),
                rule.isActive(),
                rule.getSteps().stream().map(Step::from).toList());
    }

    public record Step(int stepOrder, ApproverType approverType, Long approverUserId) {

        static Step from(ApprovalRuleStep step) {
            return new Step(step.getStepOrder(), step.getApproverType(), idOf(step.getApproverUser()));
        }
    }
}
