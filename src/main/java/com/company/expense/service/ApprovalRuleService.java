package com.company.expense.service;

import com.company.expense.dto.request.CreateApprovalRuleRequest;
import com.company.expense.dto.response.ApprovalRuleResponse;
import java.util.List;

public interface ApprovalRuleService {

    ApprovalRuleResponse createRule(Long actorId, CreateApprovalRuleRequest request);

    List<ApprovalRuleResponse> listRules();

    ApprovalRuleResponse activateRule(Long actorId, Long ruleId);

    ApprovalRuleResponse deactivateRule(Long actorId, Long ruleId);
}
