package com.company.expense.repository;

import com.company.expense.entity.AuditLog;
import com.company.expense.enums.AuditEntityType;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @EntityGraph(attributePaths = "actor")
    List<AuditLog> findTop100ByOrderByIdDesc();

    @EntityGraph(attributePaths = "actor")
    List<AuditLog> findByEntityTypeAndEntityIdOrderByIdAsc(AuditEntityType entityType, Long entityId);
}
