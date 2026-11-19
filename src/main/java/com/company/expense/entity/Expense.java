package com.company.expense.entity;

import com.company.expense.enums.ApproverType;
import com.company.expense.enums.ExpenseStatus;
import com.company.expense.enums.RoleName;
import com.company.expense.exception.BusinessRuleException;
import com.company.expense.exception.ForbiddenOperationException;
import com.company.expense.exception.ResourceNotFoundException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "expenses")
public class Expense extends TimestampedEntity {

    public static final String CURRENCY_CODE = "INR";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private User employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private ExpenseCategory category;

    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3)
    private String currencyCode = CURRENCY_CODE;

    private LocalDate expenseDate;

    private String description;

    @Enumerated(EnumType.STRING)
    private ExpenseStatus status = ExpenseStatus.DRAFT;

    private Instant submittedAt;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Receipt> receipts = new ArrayList<>();

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL)
    @OrderBy("submissionNo, stepOrder")
    private List<ExpenseApprovalStep> approvalSteps = new ArrayList<>();

    protected Expense() {
    }

    public Expense(User employee, ExpenseCategory category, BigDecimal amount, LocalDate expenseDate,
            String description) {
        if (!employee.getRole().getName().canSubmitExpenses()) {
            throw new BusinessRuleException("Admins cannot create expenses");
        }
        this.employee = employee;
        applyDetails(category, amount, expenseDate, description);
    }

    public void updateDetails(ExpenseCategory category, BigDecimal amount, LocalDate expenseDate,
            String description) {
        requireEditable();
        applyDetails(category, amount, expenseDate, description);
    }

    public Receipt addReceipt(String fileName, String contentType, String storagePath, long fileSizeBytes,
            User uploadedBy) {
        requireEditable();
        Receipt receipt = new Receipt(this, fileName, contentType, storagePath, fileSizeBytes, uploadedBy);
        receipts.add(receipt);
        return receipt;
    }

    // Approvers may already have reviewed receipts on a resubmitted expense, so those stay as evidence.
    public Receipt removeReceipt(Long receiptId) {
        if (!isNeverSubmittedDraft()) {
            throw new BusinessRuleException("Receipts can only be removed before the expense is first submitted");
        }
        Receipt receipt = findReceipt(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt", receiptId));
        receipts.remove(receipt);
        return receipt;
    }

    public boolean isNeverSubmittedDraft() {
        return status == ExpenseStatus.DRAFT && approvalSteps.isEmpty();
    }

    /**
     * Builds the approval steps for a new submission: the manager step first, then the rule's steps.
     * When the rule's first step names a Manager, that manager takes the manager step; otherwise the
     * employee's direct manager does, falling back to the department manager, and finally to the admin team.
     *
     * @param applicableRule the matched rule, or {@code null} when no rule applies
     * @return the submission number, which is greater than 1 for a resubmission
     */
    public int submit(ApprovalRule applicableRule) {
        requireEditable();
        if (!category.isActive()) {
            throw new BusinessRuleException("Category " + category.getName() + " is no longer active");
        }
        if (category.isRequiresReceipt() && receipts.isEmpty()) {
            throw new BusinessRuleException("A receipt is required for category " + category.getName());
        }

        int submissionNo = currentSubmissionNo() + 1;
        List<ApprovalRuleStep> ruleSteps = applicableRule == null ? List.of() : applicableRule.getSteps();
        User firstNamed = ruleSteps.isEmpty() ? null : ruleSteps.get(0).getApproverUser();
        boolean ruleChoosesManager = firstNamed != null
                && firstNamed.hasRole(RoleName.MANAGER) && !firstNamed.equals(employee);
        User manager = ruleChoosesManager ? resolveRuleApprover(applicableRule, ruleSteps.get(0)) : resolveManager();
        approvalSteps.add(new ExpenseApprovalStep(this, ruleChoosesManager ? applicableRule : null,
                submissionNo, 1, manager == null ? ApproverType.ADMIN_ROLE : ApproverType.MANAGER, manager));
        if (applicableRule != null) {
            List<User> namedApprovers = new ArrayList<>();
            if (manager != null) {
                namedApprovers.add(manager);
            }
            int stepOrder = 1;
            for (ApprovalRuleStep ruleStep : ruleSteps) {
                // Nobody approves their own expense; their manager in step 1 still reviews it.
                if (employee.equals(ruleStep.getApproverUser())) {
                    continue;
                }
                User approver = resolveRuleApprover(applicableRule, ruleStep);
                // A person who already approves earlier in this path would only be asked the same question twice.
                if (approver != null && namedApprovers.contains(approver)) {
                    continue;
                }
                if (approver != null) {
                    namedApprovers.add(approver);
                }
                approvalSteps.add(new ExpenseApprovalStep(this, applicableRule, submissionNo, ++stepOrder,
                        ruleStep.getApproverType(), approver));
            }
        }
        status = ExpenseStatus.SUBMITTED;
        submittedAt = Timestamps.now();
        return submissionNo;
    }

    public ExpenseApprovalStep approve(User actor, String comment) {
        ExpenseApprovalStep step = requireStepDecidableBy(actor);
        step.approve(actor, comment);
        if (actionableStep().isEmpty()) {
            status = ExpenseStatus.APPROVED;
        }
        return step;
    }

    public ExpenseApprovalStep reject(User actor, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("A reason is required to reject an expense");
        }
        ExpenseApprovalStep step = requireStepDecidableBy(actor);
        step.reject(actor, reason.trim());
        approvalSteps.stream()
                .filter(ExpenseApprovalStep::isPending)
                .forEach(ExpenseApprovalStep::skip);
        status = ExpenseStatus.REJECTED;
        return step;
    }

    public Optional<ExpenseApprovalStep> actionableStep() {
        if (status != ExpenseStatus.SUBMITTED) {
            return Optional.empty();
        }
        int current = currentSubmissionNo();
        return approvalSteps.stream()
                .filter(step -> step.getSubmissionNo() == current && step.isPending())
                .min(Comparator.comparingInt(ExpenseApprovalStep::getStepOrder));
    }

    public int currentSubmissionNo() {
        return approvalSteps.stream()
                .mapToInt(ExpenseApprovalStep::getSubmissionNo)
                .max()
                .orElse(0);
    }

    public boolean canBeDecidedBy(User user) {
        return actionableStep().filter(step -> step.canBeDecidedBy(user)).isPresent();
    }

    public boolean isOwnedBy(User user) {
        return employee.equals(user);
    }

    public boolean canBeViewedBy(User user) {
        if (isOwnedBy(user) || approvalSteps.stream().anyMatch(step -> step.involves(user))) {
            return true;
        }
        if (user.hasRole(RoleName.FINANCE) && status == ExpenseStatus.APPROVED) {
            return true;
        }
        return actionableStep().filter(step -> user.hasRole(step.getApproverType().sharedRole())).isPresent();
    }

    public boolean isEditable() {
        return status.isEditable();
    }

    public Optional<Receipt> findReceipt(Long receiptId) {
        return receipts.stream()
                .filter(receipt -> Objects.equals(receipt.getId(), receiptId))
                .findFirst();
    }

    private void applyDetails(ExpenseCategory category, BigDecimal amount, LocalDate expenseDate,
            String description) {
        if (!category.isActive()) {
            throw new BusinessRuleException("Category " + category.getName() + " is not active");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("Amount must be greater than zero");
        }
        this.category = category;
        this.amount = amount;
        this.expenseDate = Objects.requireNonNull(expenseDate, "expenseDate");
        this.description = Objects.requireNonNull(description, "description").trim();
    }

    private void requireEditable() {
        if (!isEditable()) {
            throw new BusinessRuleException("Only draft or rejected expenses can be changed or submitted");
        }
    }

    /** The direct or department manager, or {@code null} when nobody can act as this person's manager. */
    private User resolveManager() {
        User manager = employee.approvingManager();
        boolean usable = manager != null && !manager.equals(employee) && manager.canManage(employee.getDepartment());
        return usable ? manager : null;
    }

    private User resolveRuleApprover(ApprovalRule rule, ApprovalRuleStep ruleStep) {
        if (ruleStep.getApproverType() != ApproverType.SPECIFIC_USER) {
            return null;
        }
        User approver = ruleStep.getApproverUser();
        if (!approver.isActive() || approver.equals(employee) || !approver.getRole().getName().canBeNamedApprover()) {
            throw new BusinessRuleException(
                    "The approver named in rule " + rule.getName() + " cannot approve this expense");
        }
        return approver;
    }

    private ExpenseApprovalStep requireStepDecidableBy(User actor) {
        ExpenseApprovalStep step = actionableStep()
                .orElseThrow(() -> new BusinessRuleException("This expense has no pending approval step"));
        if (!step.canBeDecidedBy(actor)) {
            throw new ForbiddenOperationException("You are not the approver for the current step");
        }
        return step;
    }

    public User getEmployee() {
        return employee;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public String getDescription() {
        return description;
    }

    public ExpenseStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public List<Receipt> getReceipts() {
        return Collections.unmodifiableList(receipts);
    }

    public List<ExpenseApprovalStep> getApprovalSteps() {
        return Collections.unmodifiableList(approvalSteps);
    }
}
