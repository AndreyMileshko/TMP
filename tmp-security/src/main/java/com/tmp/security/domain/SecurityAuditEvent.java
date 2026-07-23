package com.tmp.security.domain;

import com.tmp.security.api.AuditEventId;
import com.tmp.security.api.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * Append-only Security audit event. Callers must never pass password or hash material
 * into {@code safeDescription}.
 */
public final class SecurityAuditEvent {

    private final AuditEventId id;
    private final Instant occurredAt;
    private final UserId actorUserId;
    private final String actorLoginSnapshot;
    private final AuditOperation operation;
    private final String targetType;
    private final String targetIdentifier;
    private final String safeDescription;
    private final AuditResult result;

    private SecurityAuditEvent(
            AuditEventId id,
            Instant occurredAt,
            UserId actorUserId,
            String actorLoginSnapshot,
            AuditOperation operation,
            String targetType,
            String targetIdentifier,
            String safeDescription,
            AuditResult result) {
        this.id = Objects.requireNonNull(id, "id");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.actorUserId = actorUserId;
        this.actorLoginSnapshot = Objects.requireNonNull(actorLoginSnapshot, "actorLoginSnapshot");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        this.targetIdentifier = targetIdentifier;
        this.safeDescription = Objects.requireNonNull(safeDescription, "safeDescription");
        this.result = Objects.requireNonNull(result, "result");
    }

    public static SecurityAuditEvent record(
            AuditEventId id,
            Instant occurredAt,
            UserId actorUserId,
            String actorLoginSnapshot,
            AuditOperation operation,
            String targetType,
            String targetIdentifier,
            String safeDescription,
            AuditResult result) {
        return new SecurityAuditEvent(
                id,
                occurredAt,
                actorUserId,
                actorLoginSnapshot,
                operation,
                targetType,
                targetIdentifier,
                safeDescription,
                result);
    }

    public AuditEventId id() {
        return id;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public UserId actorUserId() {
        return actorUserId;
    }

    public String actorLoginSnapshot() {
        return actorLoginSnapshot;
    }

    public AuditOperation operation() {
        return operation;
    }

    public String targetType() {
        return targetType;
    }

    public String targetIdentifier() {
        return targetIdentifier;
    }

    public String safeDescription() {
        return safeDescription;
    }

    public AuditResult result() {
        return result;
    }
}
