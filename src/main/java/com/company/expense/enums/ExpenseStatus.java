package com.company.expense.enums;

public enum ExpenseStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED;

    public boolean isEditable() {
        return this == DRAFT || this == REJECTED;
    }
}
