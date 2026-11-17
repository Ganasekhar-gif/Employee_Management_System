package com.company.expense.controller;

import com.company.expense.dto.response.AuditLogResponse;
import com.company.expense.enums.AuditEntityType;
import com.company.expense.service.AuditLogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    public AdminAuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<AuditLogResponse> listAuditLogs(
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) Long entityId) {
        return auditLogService.listAuditLogs(entityType, entityId);
    }
}
