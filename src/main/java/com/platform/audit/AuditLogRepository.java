package com.platform.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    // Existing AuditLogService methods retained for backward compatibility.
    List<AuditLog> findTop200ByOrderByCreatedAtDesc();

    List<AuditLog> findTop100ByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, Long entityId);
}


//package com.platform.audit;
//
//import org.springframework.data.jpa.repository.JpaRepository;
//
//import java.util.List;
//
//public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
//    List<AuditLog> findTop200ByOrderByCreatedAtDesc();
//    List<AuditLog> findTop100ByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);
//}
