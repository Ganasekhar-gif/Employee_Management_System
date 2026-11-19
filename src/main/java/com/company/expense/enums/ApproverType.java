package com.company.expense.enums;

public enum ApproverType {
    MANAGER,
    FINANCE_ROLE,
    SPECIFIC_USER,
    /** Used for the manager step when nobody can act as the submitter's manager. */
    ADMIN_ROLE;

    /** The role whose members share this step, or {@code null} when the step names one person. */
    public RoleName sharedRole() {
        return switch (this) {
            case FINANCE_ROLE -> RoleName.FINANCE;
            case ADMIN_ROLE -> RoleName.ADMIN;
            case MANAGER, SPECIFIC_USER -> null;
        };
    }
}
