CREATE TABLE expense_approval_steps (
    id                   BIGSERIAL    PRIMARY KEY,
    expense_id           BIGINT       NOT NULL,
    rule_id              BIGINT,
    step_order           INTEGER      NOT NULL,
    approver_type        VARCHAR(30)  NOT NULL,
    assigned_approver_id BIGINT,
    status               VARCHAR(20)  NOT NULL,
    decision_comment     VARCHAR(500),
    decided_by           BIGINT,
    decided_at           TIMESTAMPTZ,
    submission_no        INTEGER      NOT NULL DEFAULT 1,
    CONSTRAINT fk_expense_approval_steps_expense FOREIGN KEY (expense_id) REFERENCES expenses (id),
    CONSTRAINT fk_expense_approval_steps_rule FOREIGN KEY (rule_id) REFERENCES approval_rules (id),
    CONSTRAINT fk_expense_approval_steps_assignee FOREIGN KEY (assigned_approver_id) REFERENCES users (id),
    CONSTRAINT fk_expense_approval_steps_decided_by FOREIGN KEY (decided_by) REFERENCES users (id),
    CONSTRAINT uk_expense_approval_steps_order UNIQUE (expense_id, submission_no, step_order),
    CONSTRAINT ck_expense_approval_steps_type CHECK (approver_type IN ('MANAGER', 'FINANCE_ROLE', 'SPECIFIC_USER')),
    CONSTRAINT ck_expense_approval_steps_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SKIPPED')),
    CONSTRAINT ck_expense_approval_steps_assignee CHECK ((approver_type = 'FINANCE_ROLE') = (assigned_approver_id IS NULL))
);

CREATE INDEX idx_expense_approval_steps_assignee_status ON expense_approval_steps (assigned_approver_id, status);
CREATE INDEX idx_expense_approval_steps_type_status ON expense_approval_steps (approver_type, status);
