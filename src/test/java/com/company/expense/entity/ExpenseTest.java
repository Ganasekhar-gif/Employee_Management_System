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
    void managerNamedFirstByRuleReplacesDefaultManager() {
        User departmentHead = new User("head@company.com", "hash", "Dept Head", new Role(RoleName.MANAGER), engineering);
        engineering.assignManager(departmentHead);
        User newcomer = new User("ravi@company.com", "hash", "Ravi", new Role(RoleName.EMPLOYEE), engineering);
        ApprovalRule teamTravel = new ApprovalRule("Team travel", null, null, null, null, 2);
        teamTravel.addStep(ApproverType.SPECIFIC_USER, manager);
        teamTravel.addStep(ApproverType.FINANCE_ROLE, null);
        teamTravel.activate();
        Expense expense = new Expense(newcomer, meals, BigDecimal.TEN, LocalDate.now(), "Lunch");

        expense.submit(teamTravel);

        assertThat(expense.getApprovalSteps())
                .extracting(ExpenseApprovalStep::getStepOrder, ExpenseApprovalStep::getApproverType,
                        ExpenseApprovalStep::getAssignedApprover)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, ApproverType.MANAGER, manager),
                        org.assertj.core.groups.Tuple.tuple(2, ApproverType.FINANCE_ROLE, null));
    }

    @Test
    void nonManagerNamedFirstByRuleKeepsDefaultManagerFirst() {
        ApprovalRule financeFirst = new ApprovalRule("Named finance", null, null, null, null, 2);
        financeFirst.addStep(ApproverType.SPECIFIC_USER, finance);
        financeFirst.activate();
        Expense expense = expense(meals, "120.00");

        expense.submit(financeFirst);

        assertThat(expense.getApprovalSteps())
                .extracting(ExpenseApprovalStep::getAssignedApprover)
                .containsExactly(manager, finance);
    }

    @Test
    void managerSubmittingOwnExpenseSkipsRuleStepNamingThemselves() {
        User departmentHead = new User("head@company.com", "hash", "Dept Head", new Role(RoleName.MANAGER), engineering);
        manager.assignManager(departmentHead);
        ApprovalRule teamTravel = new ApprovalRule("Team travel", null, null, null, null, 2);
        teamTravel.addStep(ApproverType.SPECIFIC_USER, manager);
        teamTravel.addStep(ApproverType.FINANCE_ROLE, null);
        teamTravel.activate();
        Expense expense = new Expense(manager, meals, BigDecimal.TEN, LocalDate.now(), "Taxi");

        expense.submit(teamTravel);

        assertThat(expense.getApprovalSteps())
                .extracting(ExpenseApprovalStep::getApproverType, ExpenseApprovalStep::getAssignedApprover)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ApproverType.MANAGER, departmentHead),
                        org.assertj.core.groups.Tuple.tuple(ApproverType.FINANCE_ROLE, null));
    }

    @Test
    void goesToAdminTeamWhenNoManagerCanApprove() {
        User newcomer = new User("ravi@company.com", "hash", "Ravi", new Role(RoleName.EMPLOYEE), engineering);
        User admin = new User("admin@company.com", "hash", "Admin", new Role(RoleName.ADMIN), null);
        Expense expense = new Expense(newcomer, meals, BigDecimal.TEN, LocalDate.now(), "Lunch");

        expense.submit(null);

        assertThat(expense.getApprovalSteps()).singleElement()
                .satisfies(step -> {
                    assertThat(step.getApproverType()).isEqualTo(ApproverType.ADMIN_ROLE);
                    assertThat(step.getAssignedApprover()).isNull();
                });
        assertThat(expense.canBeDecidedBy(admin)).isTrue();
        assertThat(expense.canBeDecidedBy(manager)).isFalse();
        expense.approve(admin, null);
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.APPROVED);
    }

    @Test
    void departmentManagerWithoutOwnManagerGoesToAdminThenRuleSteps() {
        engineering.assignManager(manager);
        Expense expense = new Expense(manager, meals, BigDecimal.TEN, LocalDate.now(), "Taxi");

        expense.submit(alwaysFinance());

        assertThat(expense.getApprovalSteps())
                .extracting(ExpenseApprovalStep::getApproverType)
                .containsExactly(ApproverType.ADMIN_ROLE, ApproverType.FINANCE_ROLE);
    }

    @Test
    void financeUserCanSubmitButNotReviewOwnExpense() {
        Expense expense = new Expense(finance, meals, BigDecimal.TEN, LocalDate.now(), "Software licence");
        User admin = new User("admin@company.com", "hash", "Admin", new Role(RoleName.ADMIN), null);

        expense.submit(alwaysFinance());
        expense.approve(admin, null);

        assertThat(expense.canBeDecidedBy(finance)).isFalse();
        User colleague = new User("fin2@company.com", "hash", "Finance Two", new Role(RoleName.FINANCE), null);
        assertThat(expense.canBeDecidedBy(colleague)).isTrue();
    }

    private ApprovalRule alwaysFinance() {
        ApprovalRule rule = new ApprovalRule("Finance for all", null, null, null, null, 3);
        rule.addStep(ApproverType.FINANCE_ROLE, null);
        rule.activate();
        return rule;
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
    void ruleNamingTheEmployeesManagerDoesNotAddSecondManagerStep() {
        ApprovalRule managerAgain = new ApprovalRule("Travel sign-off", null, null, null, null, 2);
        managerAgain.addStep(ApproverType.SPECIFIC_USER, manager);
        managerAgain.addStep(ApproverType.FINANCE_ROLE, null);
        managerAgain.activate();
        Expense expense = expense(meals, "120.00");

        expense.submit(managerAgain);

        assertThat(expense.getApprovalSteps())
                .extracting(ExpenseApprovalStep::getStepOrder, ExpenseApprovalStep::getApproverType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, ApproverType.MANAGER),
                        org.assertj.core.groups.Tuple.tuple(2, ApproverType.FINANCE_ROLE));

        expense.approve(manager, null);
        assertThat(expense.actionableStep()).get()
                .extracting(ExpenseApprovalStep::getApproverType).isEqualTo(ApproverType.FINANCE_ROLE);
    }

    @Test
    void ruleWhoseOnlyStepIsTheManagerLeavesJustTheManagerStep() {
        ApprovalRule managerOnly = new ApprovalRule("Manager only", null, null, null, null, 2);
        managerOnly.addStep(ApproverType.SPECIFIC_USER, manager);
        managerOnly.activate();
        Expense expense = expense(meals, "120.00");

        expense.submit(managerOnly);
        expense.approve(manager, null);

        assertThat(expense.getApprovalSteps()).hasSize(1);
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.APPROVED);
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

    @Test
    void receiptsCanBeRemovedOnlyBeforeFirstSubmission() {
        Expense expense = expense(travel, "900.00");
        Receipt receipt = expense.addReceipt("ticket.pdf", "application/pdf", "1/ticket.pdf", 1024, employee);

        assertThat(expense.isNeverSubmittedDraft()).isTrue();
        assertThat(expense.removeReceipt(receipt.getId())).isSameAs(receipt);
        assertThat(expense.getReceipts()).isEmpty();

        Expense resubmittable = submittedWithReceipt();
        resubmittable.reject(manager, "Wrong receipt");
        Long reviewedReceiptId = resubmittable.getReceipts().get(0).getId();

        assertThat(resubmittable.isNeverSubmittedDraft()).isFalse();
        assertThat(resubmittable.isEditable()).isTrue();
        assertThatThrownBy(() -> resubmittable.removeReceipt(reviewedReceiptId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("before the expense is first submitted");
    }

    @Test
    void reportsWhetherViewerCanDecideCurrentStep() {
        Expense expense = submittedWithReceipt();

        assertThat(expense.canBeDecidedBy(manager)).isTrue();
        assertThat(expense.canBeDecidedBy(finance)).isFalse();
        assertThat(expense.canBeDecidedBy(employee)).isFalse();

        expense.approve(manager, null);
        assertThat(expense.canBeDecidedBy(manager)).isFalse();
        assertThat(expense.canBeDecidedBy(finance)).isTrue();
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
