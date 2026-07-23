package com.tmp.security.domain.repository;

import com.tmp.security.domain.AuditQueryFilter;
import com.tmp.security.domain.SecurityAuditEvent;
import java.util.List;

/**
 * Append-only Security audit repository. No update or delete operations.
 */
public interface SecurityAuditRepository {

    void append(SecurityAuditEvent event);

    List<SecurityAuditEvent> findPage(AuditQueryFilter filter, int pageIndex, int pageSize);

    long count(AuditQueryFilter filter);
}
