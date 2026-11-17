package com.company.expense.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "expense_categories")
public class ExpenseCategory extends BaseEntity {

    private String name;

    private String description;

    private boolean requiresReceipt = true;

    private boolean active = true;

    protected ExpenseCategory() {
    }

    public ExpenseCategory(String name, String description, boolean requiresReceipt) {
        this.name = Objects.requireNonNull(name, "name").trim();
        this.description = description;
        this.requiresReceipt = requiresReceipt;
    }

    public void update(String description, boolean requiresReceipt, boolean active) {
        this.description = description;
        this.requiresReceipt = requiresReceipt;
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequiresReceipt() {
        return requiresReceipt;
    }

    public boolean isActive() {
        return active;
    }
}
