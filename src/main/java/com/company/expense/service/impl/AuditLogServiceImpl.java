package com.company.expense.service.impl;

import com.company.expense.dto.response.AuditLogResponse;
import com.company.expense.entity.AuditLog;
import com.company.expense.entity.User;
import com.company.expense.enums.AuditAction;
import com.company.expense.enums.AuditEntityType;
import com.company.expense.exception.BusinessRuleException;
import com.company.expense.repository.AuditLogRepository;
import com.company.expense.service.AuditLogService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(User actor, AuditEntityType entityType, Long entityId, AuditAction action,
            Map<String, Object> details) {
        auditLogRepository.save(new AuditLog(actor, entityType, entityId, action, details));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponse> listAuditLogs(AuditEntityType entityType, Long entityId) {
        if ((entityType == null) != (entityId == null)) {
            throw new BusinessRuleException("Filter by both entityType and entityId, or by neither");
        }
        List<AuditLog> logs = entityType == null
                ? auditLogRepository.findTop100ByOrderByIdDesc()
                : auditLogRepository.findByEntityTypeAndEntityIdOrderByIdAsc(entityType, entityId);
        return logs.stream().map(AuditLogResponse::from).toList();
    }
}
