package com.tmp.security.api;

import java.time.Instant;
import java.util.List;

/**
 * Public read-only Security audit query API.
 */
public interface AuditQueryService {

    List<AuditEventSummary> queryAuditEvents(
            Instant from,
            Instant to,
            UserId actorUserId,
            String operation,
            int pageIndex,
            int pageSize);

    long countAuditEvents(Instant from, Instant to, UserId actorUserId, String operation);
}
