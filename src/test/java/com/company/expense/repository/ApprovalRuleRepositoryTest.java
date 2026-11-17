package com.company.expense.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.expense.entity.ApprovalRule;
import com.company.expense.entity.Department;
import com.company.expense.entity.ExpenseCategory;
import com.company.expense.enums.ApproverType;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@RepositoryTest
class ApprovalRuleRepositoryTest {

    @Autowired
    private ApprovalRuleRepository ruleRepository;

    @Autowired
    private ExpenseCategoryRepository categoryRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    private ExpenseCategory travel;
    private ExpenseCategory meals;
    private Department engineering;

    @BeforeEach
    void setUp() {
        travel = categoryRepository.save(new ExpenseCategory("Travel", null, true));
        meals = categoryRepository.save(new ExpenseCategory("Meals", null, false));
        engineering = departmentRepository.save(new Department("Engineering", "ENG"));
    }

    @Test
    void returnsMatchingActiveRulesInPriorityOrder() {
        ApprovalRule travelRule = save("Travel above 500", travel, null, "500.01", null, 2, true);
        ApprovalRule anyLarge = save("Anything above 10k", null, null, "10000", null, 1, true);
        save("Inactive travel", travel, null, "0", null, 3, false);
        save("Meals only", meals, null, "0", null, 4, true);
        save("Engineering cap", null, engineering, "0", "100", 5, true);

        assertThat(ruleRepository.findMatchingActiveRules(travel, engineering, new BigDecimal("20000")))
                .containsExactly(anyLarge, travelRule);
        assertThat(ruleRepository.findMatchingActiveRules(travel, engineering, new BigDecimal("500.00")))
                .isEmpty();
        assertThat(ruleRepository.findMatchingActiveRules(travel, null, new BigDecimal("600")))
                .containsExactly(travelRule);
    }

    @Test
    void allowsSharedPriorityOnlyAmongInactiveRules() {
        save("Old", null, null, "0", null, 1, false);
        save("Current", null, null, "0", null, 1, true);

        assertThat(ruleRepository.existsByPriorityAndActiveTrue(1)).isTrue();
        assertThatThrownBy(() -> save("Clash", null, null, "0", null, 1, true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ApprovalRule save(String name, ExpenseCategory category, Department department, String min, String max,
            int priority, boolean active) {
        ApprovalRule rule = new ApprovalRule(name, category, department, new BigDecimal(min),
                max == null ? null : new BigDecimal(max), priority);
        rule.addStep(ApproverType.FINANCE_ROLE, null);
        if (active) {
            rule.activate();
        }
        return ruleRepository.saveAndFlush(rule);
    }
}
