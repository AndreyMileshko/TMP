package com.tmp.security.domain;

import com.tmp.security.api.UserId;
import java.time.Instant;

/**
 * Optional filter for Security audit queries. All fields may be null (ignored).
 */
public record AuditQueryFilter(
        Instant from, Instant to, UserId actorUserId, AuditOperation operation) {
}
