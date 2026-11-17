CREATE TABLE expenses (
    id            BIGSERIAL     PRIMARY KEY,
    employee_id   BIGINT        NOT NULL,
    category_id   BIGINT        NOT NULL,
    amount        NUMERIC(12,2) NOT NULL,
    currency_code CHAR(3)       NOT NULL DEFAULT 'INR',
    expense_date  DATE          NOT NULL,
    description   VARCHAR(500)  NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    submitted_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_expenses_employee FOREIGN KEY (employee_id) REFERENCES users (id),
    CONSTRAINT fk_expenses_category FOREIGN KEY (category_id) REFERENCES expense_categories (id),
    CONSTRAINT ck_expenses_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_expenses_currency_inr CHECK (currency_code = 'INR'),
    CONSTRAINT ck_expenses_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_expenses_employee_status ON expenses (employee_id, status);
CREATE INDEX idx_expenses_status_date ON expenses (status, expense_date);

CREATE TABLE receipts (
    id              BIGSERIAL    PRIMARY KEY,
    expense_id      BIGINT       NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    storage_path    VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT       NOT NULL,
    uploaded_by     BIGINT       NOT NULL,
    uploaded_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_receipts_expense FOREIGN KEY (expense_id) REFERENCES expenses (id),
    CONSTRAINT fk_receipts_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT ck_receipts_size_positive CHECK (file_size_bytes > 0)
);

CREATE INDEX idx_receipts_expense_id ON receipts (expense_id);
