package com.company.expense.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @Size(max = 255) String description,
        @NotNull Boolean requiresReceipt,
        @NotNull Boolean active) {
}
