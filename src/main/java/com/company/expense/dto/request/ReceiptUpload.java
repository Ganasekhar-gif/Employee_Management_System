package com.company.expense.dto.request;

import org.springframework.core.io.InputStreamSource;

public record ReceiptUpload(String originalFileName, long sizeBytes, InputStreamSource content) {
}
