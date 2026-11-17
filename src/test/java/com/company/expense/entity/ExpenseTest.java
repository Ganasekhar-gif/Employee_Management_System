package com.company.expense.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.expense.enums.ApprovalStepStatus;
import com.company.expense.enums.ApproverType;
import com.company.expense.enums.ExpenseStatus;
import com.company.expense.enums.RoleName;
import com.company.expense.exception.BusinessRuleException;
import com.company.expense.exception.ForbiddenOperationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExpenseTest {

    private final Department engineering = new Department("Engineering", "ENG");
    private final ExpenseCategory travel = new ExpenseCategory("Travel", null, true);
    private final ExpenseCategory meals = new ExpenseCategory("Meals", null, false);

    private User manager;
    private User employee;
    private User finance;
    private ApprovalRule financeReview;

    @BeforeEach
    void setUp() {
        manager = new User("lead@company.com", "hash", "Team Lead", new Role(RoleName.MANAGER), engineering);
        employee = new User("asha@company.com", "hash", "Asha Rao", new Role(RoleName.EMPLOYEE), engineering);
        employee.assignManager(manager);
        finance = new User("fin@company.com", "hash", "Finance", new Role(RoleName.FINANCE), null);

        financeReview = new ApprovalRule("High value", null, null, new BigDecimal("500.01"), null, 1);
        financeReview.addStep(ApproverType.FINANCE_ROLE, null);
        financeReview.activate();
    }

    @Test
    void newExpenseIsDraftInRupees() {
        Expense expense = expense(meals, "120.00");

        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.DRAFT);
        assertThat(expense.getCurrencyCode()).isEqualTo("INR");
        assertThat(expense.isEditable()).isTrue();
    }

    @Test
    void adminCannotCreateExpenses() {
        User admin = new User("admin@company.com", "hash", "Admin", new Role(RoleName.ADMIN), null);

        assertThatThrownBy(() -> new Expense(admin, meals, BigDecimal.TEN, LocalDate.now(), "Lunch"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsInactiveCategory() {
        meals.update(null, false, false);

        assertThatThrownBy(() -> expense(meals, "120.00"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void submitRequiresReceiptWhenCategoryDemandsIt() {
        Expense expense = expense(travel, "900.00");

        assertThatThrownBy(() -> expense.submit(null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("receipt is required");
    }

    @Test
    void submitWithoutRuleCreatesOnlyManagerStep() {
        Expense expense = expense(meals, "120.00");

        int submissionNo = expense.submit(null);

        assertThat(submissionNo).isEqualTo(1);
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.SUBMITTED);
        assertThat(expense.getSubmittedAt()).isNotNull();
        assertThat(expense.getApprovalSteps()).singleElement()
                .satisfies(step -> {
                    assertThat(step.getApproverType()).isEqualTo(ApproverType.MANAGER);
                    assertThat(step.getAssignedApprover()).isSameAs(manager);
                });
    }

    @Test
    void fallsBackToDepartmentManagerWhenNoDirectManager() {
        engineering.assignManager(manager);
        User newcomer = new User("ravi@company.com", "hash", "Ravi", new Role(RoleName.EMPLOYEE), engineering);
        Expense expense = new Expense(newcomer, meals, BigDecimal.TEN, LocalDate.now(), "Lunch");

        expense.submit(null);

        assertThat(expense.getApprovalSteps().get(0).getAssignedApprover()).isSameAs(manager);
    }

    @Test
    void submitFailsWhenNoManagerCanApprove() {
        User newcomer = new User("ravi@company.com", "hash", "Ravi", new Role(RoleName.EMPLOYEE), engineering);
        Expense expense = new Expense(newcomer, meals, BigDecimal.TEN, LocalDate.now(), "Lunch");

        assertThatThrownBy(() -> expense.submit(null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No active manager");
    }

    @Test
    void approvalPathRunsManagerThenFinance() {
        Expense expense = submittedWithReceipt();

        expense.approve(manager, "Looks fine");
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.SUBMITTED);
        assertThat(expense.actionableStep()).get()
                .extracting(ExpenseApprovalStep::getApproverType).isEqualTo(ApproverType.FINANCE_ROLE);

        expense.approve(finance, null);
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.APPROVED);
        assertThat(expense.actionableStep()).isEmpty();
        assertThat(expense.getApprovalSteps())
                .extracting(ExpenseApprovalStep::getStatus)
                .containsExactly(ApprovalStepStatus.APPROVED, ApprovalStepStatus.APPROVED);
    }

    @Test
    void financeCannotDecideBeforeManager() {
        Expense expense = submittedWithReceipt();

        assertThatThrownBy(() -> expense.approve(finance, null))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void ownerCannotApproveOwnExpense() {
        Expense expense = submittedWithReceipt();

        assertThatThrownBy(() -> expense.approve(employee, null))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void rejectRequiresReason() {
        Expense expense = submittedWithReceipt();

        assertThatThrownBy(() -> expense.reject(manager, "  "))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void rejectionSkipsRemainingStepsAndAllowsResubmission() {
        Expense expense = submittedWithReceipt();

        expense.reject(manager, "Missing trip details");

        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.REJECTED);
        assertThat(expense.getApprovalSteps())
                .extracting(ExpenseApprovalStep::getStatus)
                .containsExactly(ApprovalStepStatus.REJECTED, ApprovalStepStatus.SKIPPED);
        assertThat(expense.getApprovalSteps().get(0).getDecisionComment()).isEqualTo("Missing trip details");

        expense.updateDetails(travel, new BigDecimal("950.00"), LocalDate.now(), "Client visit, Pune");
        int submissionNo = expense.submit(financeReview);

        assertThat(submissionNo).isEqualTo(2);
        assertThat(expense.getApprovalSteps()).hasSize(4);
        assertThat(expense.actionableStep()).get()
                .satisfies(step -> {
                    assertThat(step.getSubmissionNo()).isEqualTo(2);
                    assertThat(step.getStepOrder()).isEqualTo(1);
                });
    }

    @Test
    void submittedExpenseCannotBeEdited() {
        Expense expense = submittedWithReceipt();

        assertThatThrownBy(() -> expense.updateDetails(travel, BigDecimal.ONE, LocalDate.now(), "x"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> expense.addReceipt("b.pdf", "application/pdf", "1/b.pdf", 10, employee))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void financeSeesExpenseOnlyWhenItIsTheirTurnOrApproved() {
        Expense expense = submittedWithReceipt();
        User otherManager = new User("other@company.com", "hash", "Other", new Role(RoleName.MANAGER), engineering);

        assertThat(expense.canBeViewedBy(employee)).isTrue();
        assertThat(expense.canBeViewedBy(manager)).isTrue();
        assertThat(expense.canBeViewedBy(finance)).isFalse();
        assertThat(expense.canBeViewedBy(otherManager)).isFalse();

        expense.approve(manager, null);
        assertThat(expense.canBeViewedBy(finance)).isTrue();
    }

    private Expense submittedWithReceipt() {
        Expense expense = expense(travel, "900.00");
        expense.addReceipt("ticket.pdf", "application/pdf", "1/ticket.pdf", 1024, employee);
        expense.submit(financeReview);
        return expense;
    }

    private Expense expense(ExpenseCategory category, String amount) {
        return new Expense(employee, category, new BigDecimal(amount), LocalDate.now(), "Client visit");
    }
}
