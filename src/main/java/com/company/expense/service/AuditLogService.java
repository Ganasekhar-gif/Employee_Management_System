package com.company.expense.service;

import com.company.expense.dto.response.AuditLogResponse;
import com.company.expense.entity.User;
import com.company.expense.enums.AuditAction;
import com.company.expense.enums.AuditEntityType;
import java.util.List;
import java.util.Map;

public interface AuditLogService {

    /**
     * Records an action inside the caller's transaction, so the action and its audit row commit together.
     */
    void record(User actor, AuditEntityType entityType, Long entityId, AuditAction action, Map<String, Object> details);

    List<AuditLogResponse> listAuditLogs(AuditEntityType entityType, Long entityId);
}
