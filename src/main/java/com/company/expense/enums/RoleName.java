package com.company.expense.enums;

public enum RoleName {
    EMPLOYEE,
    MANAGER,
    FINANCE,
    ADMIN;

    public boolean requiresDepartment() {
        return this == EMPLOYEE || this == MANAGER;
    }

    public boolean canSubmitExpenses() {
        return this != ADMIN;
    }

    public boolean canBeNamedApprover() {
        return this == MANAGER || this == FINANCE;
    }
}
