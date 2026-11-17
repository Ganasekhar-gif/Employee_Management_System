package com.company.expense.service;

import com.company.expense.dto.request.ExpenseRequest;
import com.company.expense.dto.request.ReceiptUpload;
import com.company.expense.dto.response.ExpenseResponse;
import com.company.expense.dto.response.ExpenseSummaryResponse;
import com.company.expense.dto.response.ReceiptDownload;
import com.company.expense.enums.ExpenseStatus;
import java.util.List;

public interface ExpenseService {

    ExpenseResponse createExpense(Long actorId, ExpenseRequest request);

    ExpenseResponse updateExpense(Long actorId, Long expenseId, ExpenseRequest request);

    ExpenseResponse.ReceiptInfo uploadReceipt(Long actorId, Long expenseId, ReceiptUpload upload);

    ReceiptDownload downloadReceipt(Long actorId, Long expenseId, Long receiptId);

    ExpenseResponse submitExpense(Long actorId, Long expenseId);

    List<ExpenseSummaryResponse> listOwnExpenses(Long actorId, ExpenseStatus status);

    ExpenseResponse getExpense(Long actorId, Long expenseId);
}
