package com.company.expense.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.expense.enums.ApproverType;
import com.company.expense.enums.RoleName;
import com.company.expense.exception.BusinessRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ApprovalRuleTest {

    private final Department engineering = new Department("Engineering", "ENG");

    @Test
    void defaultsMinimumToZeroAndStartsInactive() {
        ApprovalRule rule = new ApprovalRule("Any", null, null, null, null, 1);

        assertThat(rule.getMinAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(rule.isActive()).isFalse();
    }

    @Test
    void rejectsMaximumBelowMinimum() {
        assertThatThrownBy(() -> new ApprovalRule("Bad", null, null, new BigDecimal("100"), new BigDecimal("50"), 1))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cannotActivateWithoutSteps() {
        ApprovalRule rule = new ApprovalRule("Empty", null, null, null, null, 1);

        assertThatThrownBy(rule::activate).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void managerStepIsImplicitAndCannotBeAdded() {
        ApprovalRule rule = new ApprovalRule("Any", null, null, null, null, 1);

        assertThatThrownBy(() -> rule.addStep(ApproverType.MANAGER, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void specificUserMustBeManagerOrFinance() {
        ApprovalRule rule = new ApprovalRule("Any", null, null, null, null, 1);
        User employee = new User("asha@company.com", "hash", "Asha", new Role(RoleName.EMPLOYEE), engineering);
        User director = new User("dir@company.com", "hash", "Director", new Role(RoleName.MANAGER), engineering);

        assertThatThrownBy(() -> rule.addStep(ApproverType.SPECIFIC_USER, employee))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> rule.addStep(ApproverType.SPECIFIC_USER, null))
                .isInstanceOf(BusinessRuleException.class);

        rule.addStep(ApproverType.SPECIFIC_USER, director);
        rule.addStep(ApproverType.FINANCE_ROLE, null);

        assertThat(rule.getSteps())
                .extracting(ApprovalRuleStep::getStepOrder)
                .containsExactly(1, 2);
    }

    @Test
    void rejectsSamePersonTwiceInOneRule() {
        ApprovalRule rule = new ApprovalRule("Any", null, null, null, null, 1);
        User director = new User("dir@company.com", "hash", "Director", new Role(RoleName.MANAGER), engineering);
        rule.addStep(ApproverType.SPECIFIC_USER, director);

        assertThatThrownBy(() -> rule.addStep(ApproverType.SPECIFIC_USER, director))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already a step");
    }

    @Test
    void financeStepMustNotNameUser() {
        ApprovalRule rule = new ApprovalRule("Any", null, null, null, null, 1);
        User finance = new User("fin@company.com", "hash", "Finance", new Role(RoleName.FINANCE), null);

        assertThatThrownBy(() -> rule.addStep(ApproverType.FINANCE_ROLE, finance))
                .isInstanceOf(BusinessRuleException.class);
    }
}
