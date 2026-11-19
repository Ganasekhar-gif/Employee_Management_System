package com.company.expense.service;

import com.company.expense.dto.response.ApprovalTaskResponse;
import com.company.expense.dto.response.DecisionHistoryResponse;
import com.company.expense.dto.response.ExpenseResponse;
import java.util.List;

public interface ApprovalService {

    List<ApprovalTaskResponse> listPendingApprovals(Long actorId);

    List<DecisionHistoryResponse> listDecisionHistory(Long actorId);

    ExpenseResponse approve(Long actorId, Long expenseId, String comment);

    ExpenseResponse reject(Long actorId, Long expenseId, String reason);
}
