package com.company.expense.service;

import com.company.expense.dto.request.ApprovalRuleRequest;
import com.company.expense.dto.response.ApprovalRuleResponse;
import java.util.List;

public interface ApprovalRuleService {

    ApprovalRuleResponse createRule(Long actorId, ApprovalRuleRequest request);

    List<ApprovalRuleResponse> listRules();

    ApprovalRuleResponse updateRule(Long actorId, Long ruleId, ApprovalRuleRequest request);

    ApprovalRuleResponse activateRule(Long actorId, Long ruleId);

    ApprovalRuleResponse deactivateRule(Long actorId, Long ruleId);
}
