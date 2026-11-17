package com.company.expense.service.impl;

import com.company.expense.config.ReceiptStorageProperties;
import com.company.expense.dto.request.ExpenseRequest;
import com.company.expense.dto.request.ReceiptUpload;
import com.company.expense.dto.response.ExpenseResponse;
import com.company.expense.dto.response.ExpenseSummaryResponse;
import com.company.expense.dto.response.ReceiptDownload;
import com.company.expense.entity.ApprovalRule;
import com.company.expense.entity.Expense;
import com.company.expense.entity.ExpenseCategory;
import com.company.expense.entity.Receipt;
import com.company.expense.entity.User;
import com.company.expense.enums.AuditAction;
import com.company.expense.enums.AuditEntityType;
import com.company.expense.enums.ExpenseStatus;
import com.company.expense.enums.ReceiptFileType;
import com.company.expense.exception.BusinessRuleException;
import com.company.expense.exception.ResourceNotFoundException;
import com.company.expense.repository.ExpenseCategoryRepository;
import com.company.expense.repository.ExpenseRepository;
import com.company.expense.repository.UserRepository;
import com.company.expense.service.ApprovalRuleMatcher;
import com.company.expense.service.AuditLogService;
import com.company.expense.service.ExpenseService;
import com.company.expense.service.ReceiptStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class ExpenseServiceImpl implements ExpenseService {

    private static final int MAX_FILE_NAME_LENGTH = 255;

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ApprovalRuleMatcher ruleMatcher;
    private final ReceiptStorage receiptStorage;
    private final AuditLogService auditLogService;
    private final long maxReceiptSizeBytes;

    public ExpenseServiceImpl(
            ExpenseRepository expenseRepository,
            ExpenseCategoryRepository categoryRepository,
            UserRepository userRepository,
            ApprovalRuleMatcher ruleMatcher,
            ReceiptStorage receiptStorage,
            AuditLogService auditLogService,
            ReceiptStorageProperties storageProperties) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.ruleMatcher = ruleMatcher;
        this.receiptStorage = receiptStorage;
        this.auditLogService = auditLogService;
        this.maxReceiptSizeBytes = storageProperties.maxFileSizeBytes();
    }

    @Override
    public ExpenseResponse createExpense(Long actorId, ExpenseRequest request) {
        User employee = findUser(actorId);
        Expense expense = new Expense(employee, findCategory(request.categoryId()),
                request.amount(), request.expenseDate(), request.description());
        Expense saved = expenseRepository.save(expense);
        auditLogService.record(employee, AuditEntityType.EXPENSE, saved.getId(), AuditAction.CREATE,
                Map.of("amount", saved.getAmount(), "categoryId", saved.getCategory().getId()));
        return ExpenseResponse.from(saved);
    }

    @Override
    public ExpenseResponse updateExpense(Long actorId, Long expenseId, ExpenseRequest request) {
        Expense expense = findOwnExpenseForUpdate(actorId, expenseId);
        expense.updateDetails(findCategory(request.categoryId()),
                request.amount(), request.expenseDate(), request.description());
        return ExpenseResponse.from(expense);
    }

    @Override
    public ExpenseResponse.ReceiptInfo uploadReceipt(Long actorId, Long expenseId, ReceiptUpload upload) {
        Expense expense = findOwnExpenseForUpdate(actorId, expenseId);
        if (!expense.isEditable()) {
            throw new BusinessRuleException("Receipts can only be added to draft or rejected expenses");
        }
        if (upload.sizeBytes() <= 0) {
            throw new BusinessRuleException("The receipt file is empty");
        }
        if (upload.sizeBytes() > maxReceiptSizeBytes) {
            throw new BusinessRuleException("A receipt must not be larger than " + maxReceiptSizeBytes + " bytes");
        }
        ReceiptFileType fileType = detectFileType(upload);

        String storagePath = storeContent(expenseId, fileType, upload);
        deleteFileIfTransactionRollsBack(storagePath);
        Receipt receipt = expense.addReceipt(displayFileName(upload.originalFileName(), fileType),
                fileType.mediaType(), storagePath, upload.sizeBytes(), expense.getEmployee());
        expenseRepository.flush();
        return ExpenseResponse.ReceiptInfo.from(receipt);
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptDownload downloadReceipt(Long actorId, Long expenseId, Long receiptId) {
        Expense expense = findViewableExpense(actorId, expenseId);
        Receipt receipt = expense.findReceipt(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt", receiptId));
        return new ReceiptDownload(receipt.getFileName(), receipt.getContentType(),
                receiptStorage.load(receipt.getStoragePath()));
    }

    @Override
    public ExpenseResponse submitExpense(Long actorId, Long expenseId) {
        Expense expense = findOwnExpenseForUpdate(actorId, expenseId);
        Optional<ApprovalRule> rule = ruleMatcher.findApplicableRule(expense);
        int submissionNo = expense.submit(rule.orElse(null));
        expenseRepository.flush();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("submissionNo", submissionNo);
        details.put("amount", expense.getAmount());
        rule.ifPresent(matched -> details.put("ruleId", matched.getId()));
        auditLogService.record(expense.getEmployee(), AuditEntityType.EXPENSE, expenseId,
                submissionNo == 1 ? AuditAction.SUBMIT : AuditAction.RESUBMIT, details);
        return ExpenseResponse.from(expense);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseSummaryResponse> listOwnExpenses(Long actorId, ExpenseStatus status) {
        List<Expense> expenses = status == null
                ? expenseRepository.findByEmployee_IdOrderByIdDesc(actorId)
                : expenseRepository.findByEmployee_IdAndStatusOrderByIdDesc(actorId, status);
        return expenses.stream().map(ExpenseSummaryResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseResponse getExpense(Long actorId, Long expenseId) {
        return ExpenseResponse.from(findViewableExpense(actorId, expenseId));
    }

    // Someone else's expense is reported as missing so that ids cannot be probed.
    private Expense findOwnExpenseForUpdate(Long actorId, Long expenseId) {
        User actor = findUser(actorId);
        return expenseRepository.findByIdForUpdate(expenseId)
                .filter(expense -> expense.isOwnedBy(actor))
                .orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));
    }

    private Expense findViewableExpense(Long actorId, Long expenseId) {
        User actor = findUser(actorId);
        return expenseRepository.findById(expenseId)
                .filter(expense -> expense.canBeViewedBy(actor))
                .orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));
    }

    private ReceiptFileType detectFileType(ReceiptUpload upload) {
        try (InputStream content = upload.content().getInputStream()) {
            byte[] header = content.readNBytes(ReceiptFileType.SIGNATURE_LENGTH);
            return ReceiptFileType.detect(header)
                    .orElseThrow(() -> new BusinessRuleException("A receipt must be a PDF, PNG or JPEG file"));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the uploaded receipt", exception);
        }
    }

    private String storeContent(Long expenseId, ReceiptFileType fileType, ReceiptUpload upload) {
        try (InputStream content = upload.content().getInputStream()) {
            return receiptStorage.store(expenseId, fileType.extension(), content);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the uploaded receipt", exception);
        }
    }

    private void deleteFileIfTransactionRollsBack(String storagePath) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    receiptStorage.delete(storagePath);
                }
            }
        });
    }

    private static String displayFileName(String originalFileName, ReceiptFileType fileType) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(
                originalFileName == null ? "" : originalFileName));
        if (!StringUtils.hasText(name)) {
            name = "receipt" + fileType.extension();
        }
        return name.length() > MAX_FILE_NAME_LENGTH ? name.substring(name.length() - MAX_FILE_NAME_LENGTH) : name;
    }

    private ExpenseCategory findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
