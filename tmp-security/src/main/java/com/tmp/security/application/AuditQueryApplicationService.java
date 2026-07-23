package com.tmp.security.application;

import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.domain.AuditQueryFilter;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import java.util.List;
import java.util.Objects;

/**
 * Read-only Security audit query API.
 */
public final class AuditQueryApplicationService {

    private final SecurityAuditRepository auditRepository;
    private final AuthorizationApplicationService authorization;

    public AuditQueryApplicationService(
            SecurityAuditRepository auditRepository, AuthorizationApplicationService authorization) {
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public List<SecurityAuditEvent> queryAuditEvents(AuditQueryFilter filter, int pageIndex, int pageSize) {
        authorization.requirePermission(SecurityPermissions.AUDIT_VIEW);
        return auditRepository.findPage(filter, pageIndex, pageSize);
    }

    public long countAuditEvents(AuditQueryFilter filter) {
        authorization.requirePermission(SecurityPermissions.AUDIT_VIEW);
        return auditRepository.count(filter);
    }
}
