CREATE TABLE roles (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255),
    CONSTRAINT uk_roles_name UNIQUE (name),
    CONSTRAINT ck_roles_name CHECK (name IN ('EMPLOYEE', 'MANAGER', 'FINANCE', 'ADMIN'))
);

INSERT INTO roles (name, description) VALUES
    ('EMPLOYEE', 'Submits and tracks own expenses'),
    ('MANAGER',  'Decides assigned approval steps and submits own expenses'),
    ('FINANCE',  'Exports approved expenses and decides Finance approval steps'),
    ('ADMIN',    'Maintains users, departments, categories, and approval rules');

-- departments.manager_id and users.department_id reference each other,
-- so the departments -> users foreign key is added after both tables exist.
CREATE TABLE departments (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    code       VARCHAR(20)  NOT NULL,
    manager_id BIGINT,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_departments_name UNIQUE (name),
    CONSTRAINT uk_departments_code UNIQUE (code)
);

CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    role_id       BIGINT       NOT NULL,
    department_id BIGINT,
    manager_id    BIGINT,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_users_manager FOREIGN KEY (manager_id) REFERENCES users (id),
    CONSTRAINT ck_users_not_own_manager CHECK (manager_id <> id)
);

ALTER TABLE departments
    ADD CONSTRAINT fk_departments_manager FOREIGN KEY (manager_id) REFERENCES users (id);

CREATE INDEX idx_users_manager_id ON users (manager_id);
CREATE INDEX idx_users_department_active ON users (department_id, active);
