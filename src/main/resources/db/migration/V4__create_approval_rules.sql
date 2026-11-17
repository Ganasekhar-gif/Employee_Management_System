CREATE TABLE approval_rules (
    id            BIGSERIAL     PRIMARY KEY,
    name          VARCHAR(150)  NOT NULL,
    category_id   BIGINT,
    department_id BIGINT,
    min_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,
    max_amount    NUMERIC(12,2),
    priority      INTEGER       NOT NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_approval_rules_category FOREIGN KEY (category_id) REFERENCES expense_categories (id),
    CONSTRAINT fk_approval_rules_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT ck_approval_rules_min_amount CHECK (min_amount >= 0),
    CONSTRAINT ck_approval_rules_amount_range CHECK (max_amount IS NULL OR max_amount >= min_amount)
);

CREATE UNIQUE INDEX uk_approval_rules_active_priority ON approval_rules (priority) WHERE active;

CREATE TABLE approval_rule_steps (
    id               BIGSERIAL   PRIMARY KEY,
    rule_id          BIGINT      NOT NULL,
    step_order       INTEGER     NOT NULL,
    approver_type    VARCHAR(30) NOT NULL,
    approver_user_id BIGINT,
    CONSTRAINT fk_approval_rule_steps_rule FOREIGN KEY (rule_id) REFERENCES approval_rules (id),
    CONSTRAINT fk_approval_rule_steps_user FOREIGN KEY (approver_user_id) REFERENCES users (id),
    CONSTRAINT uk_approval_rule_steps_order UNIQUE (rule_id, step_order),
    CONSTRAINT ck_approval_rule_steps_order CHECK (step_order >= 1),
    CONSTRAINT ck_approval_rule_steps_type CHECK (approver_type IN ('FINANCE_ROLE', 'SPECIFIC_USER')),
    CONSTRAINT ck_approval_rule_steps_user CHECK ((approver_type = 'SPECIFIC_USER') = (approver_user_id IS NOT NULL))
);
