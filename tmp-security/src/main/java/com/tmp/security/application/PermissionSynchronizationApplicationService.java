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
import com.tmp.security.domain.PermissionOwnershipConflictException;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.repository.PermissionDefinitionRepository;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import java.time.Clock;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
        Set<PermissionId> catalogueIds = new HashSet<>();
        for (CapabilityDescriptor descriptor : capabilityEngine.registeredCapabilities()) {
            String ownerId = descriptor.id().value();
            boolean capabilityActive =
                    capabilityEngine.stateOf(descriptor.id()) == CapabilityLifecycleState.ACTIVE;
            for (PermissionDescriptor permission : descriptor.permissions()) {
                PermissionId permissionId = PermissionId.of(permission.permissionId());
                catalogueIds.add(permissionId);
                Optional<PermissionDefinition> existing = permissionDefinitions.findById(permissionId);
                if (existing.isEmpty()) {
                    PermissionDefinition registered = PermissionDefinition.register(
                            permissionId,
                            ownerId,
                            permission.displayName(),
                            permission.description(),
                            clock);
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
                PermissionDefinition original = existing.get();
                PermissionDefinition current = original;
                if (!ownerId.equals(current.ownerCapabilityId())) {
                    if (PermissionDefinition.LEGACY_UNASSIGNED_OWNER.equals(current.ownerCapabilityId())) {
                        current = current.claimLegacyOwnership(ownerId);
                    } else {
                        throw new PermissionOwnershipConflictException(
                                "Permission '"
                                        + permissionId.value()
                                        + "' is owned by capability '"
                                        + current.ownerCapabilityId()
                                        + "' but capability '"
                                        + ownerId
                                        + "' also contributes it");
                    }
                }
                PermissionDefinition next = current;
                if (!Objects.equals(current.displayName(), permission.displayName())) {
                    next = next.withDisplayName(permission.displayName());
                }
                if (!Objects.equals(current.description(), permission.description())) {
                    next = next.withDescription(permission.description());
                }
                next = capabilityActive ? next.activated() : next.deactivated();
                if (changed(original, next)) {
                    permissionDefinitions.save(next);
                }
            }
        }
        for (PermissionDefinition definition : permissionDefinitions.findAll()) {
            if (!catalogueIds.contains(definition.permissionId()) && definition.active()) {
                permissionDefinitions.save(definition.deactivated());
            }
        }
    }

    private static boolean changed(PermissionDefinition current, PermissionDefinition next) {
        return next.active() != current.active()
                || !Objects.equals(next.ownerCapabilityId(), current.ownerCapabilityId())
                || !Objects.equals(next.displayName(), current.displayName())
                || !Objects.equals(next.description(), current.description());
    }
}
