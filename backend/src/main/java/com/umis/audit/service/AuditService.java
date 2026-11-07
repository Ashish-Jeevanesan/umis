package com.umis.audit.service;

import com.umis.audit.model.AuditLog;
import com.umis.audit.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service recording administrative and security actions in the audit log.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(UUID tenantId, String actorId, String action, String resourceType, String resourceId, String details) {
        AuditLog log = new AuditLog(tenantId, actorId, action, resourceType, resourceId, details);
        auditLogRepository.save(log);
    }
}
