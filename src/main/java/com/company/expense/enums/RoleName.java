package com.company.expense.enums;

public enum RoleName {
    EMPLOYEE,
    MANAGER,
    FINANCE,
    ADMIN;

    public boolean requiresDepartment() {
        return this == EMPLOYEE || this == MANAGER;
    }
}
