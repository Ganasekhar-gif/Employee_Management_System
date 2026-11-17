package com.company.expense.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.receipts")
public record ReceiptStorageProperties(Path storageDirectory, long maxFileSizeBytes) {
}
