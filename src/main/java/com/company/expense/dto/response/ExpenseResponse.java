package com.company.expense.dto.response;

import static com.company.expense.dto.response.EntityReferences.idOf;
import static com.company.expense.dto.response.EntityReferences.nameOf;

import com.company.expense.entity.Expense;
import com.company.expense.entity.ExpenseApprovalStep;
import com.company.expense.entity.Receipt;
import com.company.expense.entity.User;
import com.company.expense.enums.ApprovalStepStatus;
import com.company.expense.enums.ApproverType;
import com.company.expense.enums.ExpenseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ExpenseResponse(
        Long id,
        Long employeeId,
        String employeeName,
        Long categoryId,
        String categoryName,
        boolean receiptRequired,
        BigDecimal amount,
        String currencyCode,
        LocalDate expenseDate,
        String description,
        ExpenseStatus status,
        Instant submittedAt,
        Instant createdAt,
        List<ReceiptInfo> receipts,
        List<ApprovalStep> approvalSteps,
        Permissions permissions) {

    public static ExpenseResponse from(Expense expense, User viewer) {
        boolean owner = expense.isOwnedBy(viewer);
        return new ExpenseResponse(
                expense.getId(),
                expense.getEmployee().getId(),
                expense.getEmployee().getFullName(),
                expense.getCategory().getId(),
                expense.getCategory().getName(),
                expense.getCategory().isRequiresReceipt(),
                expense.getAmount(),
                expense.getCurrencyCode(),
                expense.getExpenseDate(),
                expense.getDescription(),
                expense.getStatus(),
                expense.getSubmittedAt(),
                expense.getCreatedAt(),
                expense.getReceipts().stream().map(ReceiptInfo::from).toList(),
                expense.getApprovalSteps().stream().map(ApprovalStep::from).toList(),
                new Permissions(
                        owner && expense.isEditable(),
                        owner && expense.isNeverSubmittedDraft(),
                        expense.canBeDecidedBy(viewer)));
    }

    /**
     * What the viewer may do next, so clients do not have to re-implement the workflow rules.
     *
     * @param canEdit edit details, add receipts and submit
     * @param canDelete delete the draft or remove its receipts
     * @param canDecide approve or reject the current step
     */
    public record Permissions(boolean canEdit, boolean canDelete, boolean canDecide) {
    }

    public record ReceiptInfo(Long id, String fileName, String contentType, long fileSizeBytes, Instant uploadedAt) {

        public static ReceiptInfo from(Receipt receipt) {
            return new ReceiptInfo(receipt.getId(), receipt.getFileName(), receipt.getContentType(),
                    receipt.getFileSizeBytes(), receipt.getUploadedAt());
        }
    }

    public record ApprovalStep(
            int submissionNo,
            int stepOrder,
            ApproverType approverType,
            Long assignedApproverId,
            String assignedApproverName,
            ApprovalStepStatus status,
            String comment,
            Long decidedById,
            String decidedByName,
            Instant decidedAt) {

        static ApprovalStep from(ExpenseApprovalStep step) {
            return new ApprovalStep(
                    step.getSubmissionNo(),
                    step.getStepOrder(),
                    step.getApproverType(),
                    idOf(step.getAssignedApprover()),
                    nameOf(step.getAssignedApprover()),
                    step.getStatus(),
                    step.getDecisionComment(),
                    idOf(step.getDecidedBy()),
                    nameOf(step.getDecidedBy()),
                    step.getDecidedAt());
        }
    }
}
