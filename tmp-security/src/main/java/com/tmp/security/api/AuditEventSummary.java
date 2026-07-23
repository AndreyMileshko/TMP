package com.tmp.security.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Display-safe audit event summary. Never carries password or hash.
 */
public final class AuditEventSummary {

    private final AuditEventId id;
    private final Instant occurredAt;
    private final UserId actorUserId;
    private final String actorLogin;
    private final String operation;
    private final String targetType;
    private final String targetIdentifier;
    private final String safeDescription;
    private final String result;

    public AuditEventSummary(
            AuditEventId id,
            Instant occurredAt,
            UserId actorUserId,
            String actorLogin,
            String operation,
            String targetType,
            String targetIdentifier,
            String safeDescription,
            String result) {
        this.id = Objects.requireNonNull(id, "id");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.actorUserId = actorUserId;
        this.actorLogin = Objects.requireNonNull(actorLogin, "actorLogin");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        this.targetIdentifier = targetIdentifier;
        this.safeDescription = Objects.requireNonNull(safeDescription, "safeDescription");
        this.result = Objects.requireNonNull(result, "result");
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

    public String actorLogin() {
        return actorLogin;
    }

    public String operation() {
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

    public String result() {
        return result;
    }

    @Override
    public String toString() {
        return "AuditEventSummary{id=" + id + ", operation=" + operation + "}";
    }
}
