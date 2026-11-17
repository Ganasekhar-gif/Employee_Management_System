package com.company.expense.entity;

import com.company.expense.enums.RoleName;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "roles")
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private RoleName name;

    private String description;

    protected Role() {
    }

    Role(RoleName name) {
        this.name = name;
    }

    public RoleName getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
