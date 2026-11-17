package com.company.expense.dto.response;

import org.springframework.core.io.Resource;

public record ReceiptDownload(String fileName, String contentType, Resource content) {
}
