package com.tmp.security.application;

import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.security.api.AuditEventId;
import com.tmp.security.api.PermissionId;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditResult;
import com.tmp.security.domain.PermissionDefinition;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.repository.PermissionDefinitionRepository;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synchronizes Security's permission-definition registry with Capability Engine catalogues.
 */
public class PermissionSynchronizationApplicationService {

    private final CapabilityEngine capabilityEngine;
    private final PermissionDefinitionRepository permissionDefinitions;
    private final SecurityAuditRepository auditRepository;
    private final Clock clock;

    public PermissionSynchronizationApplicationService(
            CapabilityEngine capabilityEngine,
            PermissionDefinitionRepository permissionDefinitions,
            SecurityAuditRepository auditRepository,
            Clock clock) {
        this.capabilityEngine = Objects.requireNonNull(capabilityEngine, "capabilityEngine");
        this.permissionDefinitions = Objects.requireNonNull(permissionDefinitions, "permissionDefinitions");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public void synchronize() {
        for (CapabilityDescriptor descriptor : capabilityEngine.registeredCapabilities()) {
            boolean capabilityActive =
                    capabilityEngine.stateOf(descriptor.id()) == CapabilityLifecycleState.ACTIVE;
            for (PermissionDescriptor permission : descriptor.permissions()) {
                PermissionId permissionId = PermissionId.of(permission.permissionId());
                Optional<PermissionDefinition> existing = permissionDefinitions.findById(permissionId);
                if (existing.isEmpty()) {
                    PermissionDefinition registered = PermissionDefinition.register(
                            permissionId, permission.displayName(), permission.description(), clock);
                    if (!capabilityActive) {
                        registered = registered.deactivated();
                    }
                    permissionDefinitions.save(registered);
                    auditRepository.append(SecurityAuditEvent.record(
                            AuditEventId.generate(),
                            clock.instant(),
                            null,
                            "system",
                            AuditOperation.PERMISSION_DEFINITION_REGISTERED,
                            "PERMISSION",
                            permissionId.value(),
                            "Registered permission definition " + permissionId.value(),
                            AuditResult.SUCCESS));
                    continue;
                }
                PermissionDefinition current = existing.get();
                PermissionDefinition next = current;
                if (!Objects.equals(current.displayName(), permission.displayName())) {
                    next = next.withDisplayName(permission.displayName());
                }
                if (!Objects.equals(current.description(), permission.description())) {
                    next = next.withDescription(permission.description());
                }
                next = capabilityActive ? next.activated() : next.deactivated();
                if (next != current
                        && (next.active() != current.active()
                                || !Objects.equals(next.displayName(), current.displayName())
                                || !Objects.equals(next.description(), current.description()))) {
                    permissionDefinitions.save(next);
                }
            }
        }
    }
}
