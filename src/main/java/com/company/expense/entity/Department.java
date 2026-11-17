package com.company.expense.entity;

import com.company.expense.exception.BusinessRuleException;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "departments")
public class Department extends BaseEntity {

    private String name;

    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    private boolean active = true;

    protected Department() {
    }

    public Department(String name, String code) {
        this.name = Objects.requireNonNull(name, "name").trim();
        this.code = Objects.requireNonNull(code, "code").trim().toUpperCase(Locale.ROOT);
    }

    public void assignManager(User manager) {
        if (!manager.canManage(this)) {
            throw new BusinessRuleException("Department manager must be an active Manager in this department");
        }
        this.manager = manager;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public User getManager() {
        return manager;
    }

    public boolean isActive() {
        return active;
    }
}
