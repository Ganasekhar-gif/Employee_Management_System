ALTER TABLE expense_approval_steps
    DROP CONSTRAINT ck_expense_approval_steps_type,
    DROP CONSTRAINT ck_expense_approval_steps_assignee;

ALTER TABLE expense_approval_steps
    ADD CONSTRAINT ck_expense_approval_steps_type
        CHECK (approver_type IN ('MANAGER', 'FINANCE_ROLE', 'SPECIFIC_USER', 'ADMIN_ROLE')),
    ADD CONSTRAINT ck_expense_approval_steps_assignee
        CHECK ((approver_type IN ('FINANCE_ROLE', 'ADMIN_ROLE')) = (assigned_approver_id IS NULL));
