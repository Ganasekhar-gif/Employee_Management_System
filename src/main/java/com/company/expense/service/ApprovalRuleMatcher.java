package com.company.expense.service;

import com.company.expense.entity.ApprovalRule;
import com.company.expense.entity.Expense;
import com.company.expense.repository.ApprovalRuleRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ApprovalRuleMatcher {

    private final ApprovalRuleRepository ruleRepository;

    public ApprovalRuleMatcher(ApprovalRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public Optional<ApprovalRule> findApplicableRule(Expense expense) {
        return ruleRepository.findMatchingActiveRules(
                        expense.getCategory(), expense.getEmployee().getDepartment(), expense.getAmount())
                .stream()
                .findFirst();
    }
}
