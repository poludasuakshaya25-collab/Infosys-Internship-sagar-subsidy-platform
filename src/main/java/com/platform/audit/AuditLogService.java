package com.platform.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditLogService {

    @Autowired private AuditLogRepository repository;

    @Transactional
    public AuditLog record(String entityType, Long entityId, String action,
                           String previousValue, String newValue, String remarks) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actorEmail = "SYSTEM";
        String actorRole = "SYSTEM";

        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null
                && !"anonymousUser".equalsIgnoreCase(authentication.getName())) {
            actorEmail = authentication.getName();
            actorRole = authentication.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .findFirst()
                    .orElse("AUTHENTICATED_USER");
        }

        return recordAs(actorEmail, actorRole, entityType, entityId, action,
                previousValue, newValue, remarks);
    }

    @Transactional
    public AuditLog recordAs(String actorEmail, String actorRole,
                             String entityType, Long entityId, String action,
                             String previousValue, String newValue, String remarks) {
        AuditLog log = new AuditLog();
        log.setActorEmail(clean(actorEmail, "SYSTEM"));
        log.setActorRole(clean(actorRole, "SYSTEM"));
        log.setEntityType(clean(entityType, "UNKNOWN"));
        log.setEntityId(entityId);
        log.setAction(clean(action, "ACTION"));
        log.setPreviousValue(limit(previousValue, 1200));
        log.setNewValue(limit(newValue, 1200));
        log.setRemarks(limit(remarks, 2000));
        return repository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getRecentLogs() {
        return repository.findTop200ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getEntityLogs(String entityType, Long entityId) {
        return repository.findTop100ByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }

    private String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
