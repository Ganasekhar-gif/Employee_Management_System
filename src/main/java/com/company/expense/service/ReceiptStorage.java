package com.company.expense.service;

import java.io.InputStream;
import org.springframework.core.io.Resource;

public interface ReceiptStorage {

    /**
     * Stores the content and returns the storage path to save in {@code receipts.storage_path}.
     */
    String store(Long expenseId, String extension, InputStream content);

    Resource load(String storagePath);

    void delete(String storagePath);
}
