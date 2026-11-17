package com.company.expense.dto.response;

import com.company.expense.entity.ExpenseCategory;

public record CategoryResponse(Long id, String name, String description, boolean requiresReceipt, boolean active) {

    public static CategoryResponse from(ExpenseCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.isRequiresReceipt(),
                category.isActive());
    }
}
