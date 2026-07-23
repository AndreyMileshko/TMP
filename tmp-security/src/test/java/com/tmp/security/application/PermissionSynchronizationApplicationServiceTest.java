package com.tmp.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.api.CapabilityEngineStatus;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.capability.api.ViewDescriptor;
import com.tmp.security.api.PermissionId;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditQueryFilter;
import com.tmp.security.domain.PermissionDefinition;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.repository.PermissionDefinitionRepository;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PermissionSynchronizationApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);
    private static final CapabilityId CAP_ID = CapabilityId.of("security-administration");
    private static final PermissionId VIEW = PermissionId.of("security.users.view");

    @Test
    void registersThenDeactivatesWithoutDeletingOrDuplicatingAudit() {
        FakeCapabilityEngine engine = new FakeCapabilityEngine();
        engine.put(descriptor(), CapabilityLifecycleState.ACTIVE);
        InMemoryPermissionDefinitions definitions = new InMemoryPermissionDefinitions();
        InMemoryAudit audit = new InMemoryAudit();
        PermissionSynchronizationApplicationService service =
                new PermissionSynchronizationApplicationService(engine, definitions, audit, CLOCK);

        service.synchronize();
        PermissionDefinition registered = definitions.findById(VIEW).orElseThrow();
        assertTrue(registered.active());
        assertEquals(CAP_ID.value(), registered.ownerCapabilityId());
        assertEquals(1, audit.events.size());
        assertEquals(AuditOperation.PERMISSION_DEFINITION_REGISTERED, audit.events.getFirst().operation());

        service.synchronize();
        assertEquals(1, audit.events.size());

        engine.put(descriptor(), CapabilityLifecycleState.REGISTERED);
        service.synchronize();
        assertFalse(definitions.findById(VIEW).orElseThrow().active());
        assertEquals(1, definitions.findAll().size());

        engine.put(descriptor(), CapabilityLifecycleState.ACTIVE);
        service.synchronize();
        assertTrue(definitions.findById(VIEW).orElseThrow().active());
        assertEquals(1, audit.events.size());
    }

    @Test
    void ownershipConflictIsDetected() {
        FakeCapabilityEngine engine = new FakeCapabilityEngine();
        engine.put(descriptor(), CapabilityLifecycleState.ACTIVE);
        InMemoryPermissionDefinitions definitions = new InMemoryPermissionDefinitions();
        PermissionSynchronizationApplicationService service =
                new PermissionSynchronizationApplicationService(engine, definitions, new InMemoryAudit(), CLOCK);
        service.synchronize();

        CapabilityId other = CapabilityId.of("other.capability");
        engine.put(
                CapabilityDescriptor.builder()
                        .id(other)
                        .name("Other")
                        .version(CapabilityVersion.of("1.0.0"))
                        .description("other")
                        .permissions(List.of(PermissionDescriptor.of(VIEW.value(), "View users", "desc")))
                        .build(),
                CapabilityLifecycleState.ACTIVE);

        assertThrows(
                com.tmp.security.domain.PermissionOwnershipConflictException.class, service::synchronize);
    }

    @Test
    void orphanDefinitionIsDeactivatedWhenMissingFromCatalogue() {
        FakeCapabilityEngine engine = new FakeCapabilityEngine();
        engine.put(descriptor(), CapabilityLifecycleState.ACTIVE);
        InMemoryPermissionDefinitions definitions = new InMemoryPermissionDefinitions();
        PermissionSynchronizationApplicationService service =
                new PermissionSynchronizationApplicationService(engine, definitions, new InMemoryAudit(), CLOCK);
        service.synchronize();

        PermissionId orphan = PermissionId.of("security.orphan.view");
        definitions.save(PermissionDefinition.register(
                orphan, "gone.capability", "Orphan", "", CLOCK));

        service.synchronize();
        assertFalse(definitions.findById(orphan).orElseThrow().active());
        assertTrue(definitions.findById(VIEW).orElseThrow().active());
    }

    private static CapabilityDescriptor descriptor() {
        return CapabilityDescriptor.builder()
                .id(CAP_ID)
                .name("Security Administration")
                .version(CapabilityVersion.of("1.0.0"))
                .description("test")
                .permissions(List.of(PermissionDescriptor.of(
                        VIEW.value(), "View users", "desc")))
                .build();
    }

    private static final class FakeCapabilityEngine implements CapabilityEngine {
        private final Map<CapabilityId, CapabilityDescriptor> descriptors = new HashMap<>();
        private final Map<CapabilityId, CapabilityLifecycleState> states = new HashMap<>();

        void put(CapabilityDescriptor descriptor, CapabilityLifecycleState state) {
            descriptors.put(descriptor.id(), descriptor);
            states.put(descriptor.id(), state);
        }

        @Override
        public void discoverAndRegisterAll() {
        }

        @Override
        public void activateAll() {
        }

        @Override
        public void deactivate(CapabilityId id) {
        }

        @Override
        public void stopAll() {
        }

        @Override
        public Optional<CapabilityDescriptor> findById(CapabilityId id) {
            return Optional.ofNullable(descriptors.get(id));
        }

        @Override
        public List<CapabilityDescriptor> registeredCapabilities() {
            return List.copyOf(descriptors.values());
        }

        @Override
        public CapabilityLifecycleState stateOf(CapabilityId id) {
            return states.getOrDefault(id, CapabilityLifecycleState.REGISTERED);
        }

        @Override
        public List<PermissionDescriptor> activePermissions() {
            return List.of();
        }

        @Override
        public List<CommandDescriptor> activeCommands() {
            return List.of();
        }

        @Override
        public List<ViewDescriptor> activeViews() {
            return List.of();
        }

        @Override
        public List<NavigationContribution> activeNavigation() {
            return List.of();
        }

        @Override
        public CapabilityEngineStatus status() {
            return new CapabilityEngineStatus(0, descriptors.size(), 0, 0);
        }
    }

    private static final class InMemoryPermissionDefinitions implements PermissionDefinitionRepository {
        private final Map<PermissionId, PermissionDefinition> store = new HashMap<>();

        @Override
        public PermissionDefinition save(PermissionDefinition definition) {
            store.put(definition.permissionId(), definition);
            return definition;
        }

        @Override
        public Optional<PermissionDefinition> findById(PermissionId permissionId) {
            return Optional.ofNullable(store.get(permissionId));
        }

        @Override
        public List<PermissionDefinition> findAll() {
            return List.copyOf(store.values());
        }
    }

    private static final class InMemoryAudit implements SecurityAuditRepository {
        private final List<SecurityAuditEvent> events = new ArrayList<>();

        @Override
        public void append(SecurityAuditEvent event) {
            events.add(event);
        }

        @Override
        public List<SecurityAuditEvent> findPage(AuditQueryFilter filter, int pageIndex, int pageSize) {
            return List.of();
        }

        @Override
        public long count(AuditQueryFilter filter) {
            return events.size();
        }
    }
}
