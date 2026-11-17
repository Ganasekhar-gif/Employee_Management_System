CREATE TABLE expense_categories (
    id               BIGSERIAL    PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    description      VARCHAR(255),
    requires_receipt BOOLEAN      NOT NULL DEFAULT TRUE,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_expense_categories_name UNIQUE (name)
);
