package com.company.expense.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "receipts")
public class Receipt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id")
    private Expense expense;

    private String fileName;

    private String contentType;

    private String storagePath;

    private long fileSizeBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    private Instant uploadedAt;

    protected Receipt() {
    }

    Receipt(Expense expense, String fileName, String contentType, String storagePath, long fileSizeBytes,
            User uploadedBy) {
        this.expense = expense;
        this.fileName = fileName;
        this.contentType = contentType;
        this.storagePath = storagePath;
        this.fileSizeBytes = fileSizeBytes;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Timestamps.now();
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
