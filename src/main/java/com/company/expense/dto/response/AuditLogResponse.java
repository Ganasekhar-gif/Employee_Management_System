package com.company.expense.dto.response;

import static com.company.expense.dto.response.EntityReferences.idOf;
import static com.company.expense.dto.response.EntityReferences.nameOf;

import com.company.expense.entity.AuditLog;
import com.company.expense.enums.AuditAction;
import com.company.expense.enums.AuditEntityType;
import java.time.Instant;
import java.util.Map;

public record AuditLogResponse(
        Long id,
        Long actorId,
        String actorName,
        AuditEntityType entityType,
        Long entityId,
        AuditAction action,
        Map<String, Object> details,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                idOf(log.getActor()),
                nameOf(log.getActor()),
                log.getEntityType(),
                log.getEntityId(),
                log.getAction(),
                log.getDetails(),
                log.getCreatedAt());
    }
}
