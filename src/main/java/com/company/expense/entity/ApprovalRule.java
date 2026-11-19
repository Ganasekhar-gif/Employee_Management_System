package com.company.expense.entity;

import com.company.expense.enums.ApproverType;
import com.company.expense.exception.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "approval_rules")
public class ApprovalRule extends TimestampedEntity {

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ExpenseCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    private BigDecimal minAmount;

    private BigDecimal maxAmount;

    private int priority;

    private boolean active;

    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder")
    private List<ApprovalRuleStep> steps = new ArrayList<>();

    protected ApprovalRule() {
    }

    public ApprovalRule(String name, ExpenseCategory category, Department department,
            BigDecimal minAmount, BigDecimal maxAmount, int priority) {
        BigDecimal min = minAmount == null ? BigDecimal.ZERO : minAmount;
        if (min.signum() < 0) {
            throw new BusinessRuleException("Minimum amount cannot be negative");
        }
        if (maxAmount != null && maxAmount.compareTo(min) < 0) {
            throw new BusinessRuleException("Maximum amount must be greater than or equal to the minimum amount");
        }
        this.name = Objects.requireNonNull(name, "name").trim();
        this.category = category;
        this.department = department;
        this.minAmount = min;
        this.maxAmount = maxAmount;
        this.priority = priority;
    }

    public void addStep(ApproverType approverType, User approverUser) {
        switch (approverType) {
            case MANAGER -> throw new BusinessRuleException(
                    "Manager approval is always the first step and cannot be added to a rule");
            case FINANCE_ROLE -> {
                if (approverUser != null) {
                    throw new BusinessRuleException("A FINANCE_ROLE step must not name a user");
                }
            }
            case SPECIFIC_USER -> {
                if (approverUser == null || !approverUser.isActive()
                        || !approverUser.getRole().getName().canBeNamedApprover()) {
                    throw new BusinessRuleException("A SPECIFIC_USER step must name an active Manager or Finance user");
                }
                if (steps.stream().anyMatch(step -> approverUser.equals(step.getApproverUser()))) {
                    throw new BusinessRuleException(approverUser.getFullName() + " is already a step in this rule");
                }
            }
        }
        steps.add(new ApprovalRuleStep(this, steps.size() + 1, approverType, approverUser));
    }

    public void activate() {
        if (steps.isEmpty()) {
            throw new BusinessRuleException("An active rule must have at least one step");
        }
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getName() {
        return name;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public Department getDepartment() {
        return department;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isActive() {
        return active;
    }

    public List<ApprovalRuleStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }
}
