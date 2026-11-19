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
            throw new BusinessRuleException("Only Employee and Manager users can create expenses");
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
        User manager = resolveManager();
        approvalSteps.add(new ExpenseApprovalStep(
                this, null, submissionNo, 1, ApproverType.MANAGER, manager));
        if (applicableRule != null) {
            List<User> namedApprovers = new ArrayList<>(List.of(manager));
            int stepOrder = 1;
            for (ApprovalRuleStep ruleStep : applicableRule.getSteps()) {
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
        if (user.hasRole(RoleName.FINANCE)) {
            return status == ExpenseStatus.APPROVED
                    || actionableStep().filter(step -> step.getApproverType() == ApproverType.FINANCE_ROLE).isPresent();
        }
        return false;
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

    private User resolveManager() {
        User manager = employee.approvingManager();
        if (manager == null || manager.equals(employee) || !manager.canManage(employee.getDepartment())) {
            throw new BusinessRuleException("No active manager is available to approve this expense");
        }
        return manager;
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
