package com.tmp.security.application;

import com.tmp.security.api.AuditEventSummary;
import com.tmp.security.api.PermissionSummary;
import com.tmp.security.api.RoleSummary;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserSummary;
import com.tmp.security.domain.PermissionDefinition;
import com.tmp.security.domain.Role;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.Session;
import com.tmp.security.domain.User;

final class SecurityApiMapper {

    private SecurityApiMapper() {
    }

    static UserSummary toSummary(User user) {
        return new UserSummary(
                user.id(),
                user.login(),
                user.displayName(),
                user.status().name(),
                user.version(),
                user.createdAt(),
                user.updatedAt());
    }

    static RoleSummary toSummary(Role role) {
        return new RoleSummary(
                role.id(),
                role.name(),
                role.description(),
                role.permissions(),
                role.version(),
                role.createdAt(),
                role.updatedAt());
    }

    static SessionSummary toSummary(Session session) {
        return new SessionSummary(session.id(), session.userId(), session.login(), session.startedAt());
    }

    static AuditEventSummary toSummary(SecurityAuditEvent event) {
        return new AuditEventSummary(
                event.id(),
                event.occurredAt(),
                event.actorUserId(),
                event.actorLoginSnapshot(),
                event.operation().name(),
                event.targetType(),
                event.targetIdentifier(),
                event.safeDescription(),
                event.result().name());
    }

    static PermissionSummary toSummary(PermissionDefinition definition) {
        return new PermissionSummary(
                definition.permissionId(),
                definition.displayName(),
                definition.description(),
                definition.active());
    }
}
