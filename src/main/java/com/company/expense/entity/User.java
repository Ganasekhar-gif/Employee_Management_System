package com.company.expense.entity;

import com.company.expense.enums.RoleName;
import com.company.expense.exception.BusinessRuleException;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    private String email;

    private String passwordHash;

    private String fullName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id")
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    private boolean active = true;

    private Instant createdAt;

    private Instant updatedAt;

    protected User() {
    }

    public User(String email, String passwordHash, String fullName, Role role, Department department) {
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.fullName = Objects.requireNonNull(fullName, "fullName").trim();
        this.role = Objects.requireNonNull(role, "role");
        if (role.getName().requiresDepartment() && department == null) {
            throw new BusinessRuleException(role.getName() + " users must belong to a department");
        }
        this.department = department;
    }

    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email").trim().toLowerCase(Locale.ROOT);
    }

    public void assignManager(User manager) {
        if (this.equals(manager)) {
            throw new BusinessRuleException("A user cannot be their own manager");
        }
        if (!manager.canManage(department)) {
            throw new BusinessRuleException("Direct manager must be an active Manager in the same department");
        }
        this.manager = manager;
    }

    public boolean canManage(Department targetDepartment) {
        return active
                && hasRole(RoleName.MANAGER)
                && targetDepartment != null
                && targetDepartment.equals(department);
    }

    public boolean hasRole(RoleName roleName) {
        return role.getName() == roleName;
    }

    public void deactivate() {
        this.active = false;
    }

    @PrePersist
    void onCreate() {
        createdAt = now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = now();
    }

    // PostgreSQL TIMESTAMPTZ keeps microseconds; truncating here keeps the saved and reloaded values identical.
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public Department getDepartment() {
        return department;
    }

    public User getManager() {
        return manager;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
