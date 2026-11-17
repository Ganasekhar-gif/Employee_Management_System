package com.company.expense.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        Boolean requiresReceipt) {

    public boolean requiresReceiptOrDefault() {
        return requiresReceipt == null || requiresReceipt;
    }
}
