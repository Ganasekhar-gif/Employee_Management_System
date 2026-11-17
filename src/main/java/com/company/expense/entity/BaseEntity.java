package com.company.expense.entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.Hibernate;

@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getId() {
        return id;
    }

    // Uses getId() and Hibernate.getClass() so a lazy proxy equals the entity it stands in for.
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that) || Hibernate.getClass(this) != Hibernate.getClass(that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    // Constant per class because the id is assigned only after insert.
    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
