package com.company.expense.entity;

import com.company.expense.enums.AuditAction;
import com.company.expense.enums.AuditEntityType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    private AuditEntityType entityType;

    private Long entityId;

    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details;

    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(User actor, AuditEntityType entityType, Long entityId, AuditAction action,
            Map<String, Object> details) {
        this.actor = actor;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.details = details;
        this.createdAt = Timestamps.now();
    }

    public User getActor() {
        return actor;
    }

    public AuditEntityType getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public AuditAction getAction() {
        return action;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
