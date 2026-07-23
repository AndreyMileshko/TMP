package com.tmp.security.application;

import com.tmp.security.api.AuditEventSummary;
import com.tmp.security.api.AuditQueryService;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditQueryFilter;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class DefaultAuditQueryService implements AuditQueryService {

    private final AuditQueryApplicationService delegate;

    public DefaultAuditQueryService(AuditQueryApplicationService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public List<AuditEventSummary> queryAuditEvents(
            Instant from,
            Instant to,
            UserId actorUserId,
            String operation,
            int pageIndex,
            int pageSize) {
        return delegate.queryAuditEvents(toFilter(from, to, actorUserId, operation), pageIndex, pageSize)
                .stream()
                .map(SecurityApiMapper::toSummary)
                .toList();
    }

    @Override
    public long countAuditEvents(Instant from, Instant to, UserId actorUserId, String operation) {
        return delegate.countAuditEvents(toFilter(from, to, actorUserId, operation));
    }

    private static AuditQueryFilter toFilter(
            Instant from, Instant to, UserId actorUserId, String operation) {
        AuditOperation op = operation == null || operation.isBlank()
                ? null
                : AuditOperation.valueOf(operation);
        return new AuditQueryFilter(from, to, actorUserId, op);
    }
}
