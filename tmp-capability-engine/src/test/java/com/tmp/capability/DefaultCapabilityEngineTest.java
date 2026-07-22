package com.tmp.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityEngineStatus;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.DependencyDescriptor;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.capability.api.ViewDescriptor;
import com.tmp.capability.contribution.CapabilityContributionCatalogs;
import com.tmp.capability.discovery.CapabilityDiscovery;
import com.tmp.capability.lifecycle.CapabilityLifecycleManager;
import com.tmp.capability.registration.CapabilityRegistrationService;
import com.tmp.capability.registry.CapabilityRegistry;
import com.tmp.core.api.EventBus;
import com.tmp.core.api.LifecycleManager;
import com.tmp.core.api.PlatformConfiguration;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.PlatformRegistry;
import com.tmp.core.api.PlatformStatus;
import com.tmp.core.api.ServiceRegistry;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.core.event.SynchronousEventBus;
import com.tmp.core.registry.DefaultCapabilityRegistry;
import com.tmp.core.registry.DefaultServiceRegistry;
import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentEngineStatus;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentTypeDescriptor;
import com.tmp.document.api.UpdateDocumentCommand;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultCapabilityEngineTest {

    private DefaultCapabilityEngine engine;

    @BeforeEach
    void setUp() {
        Capability root = capability(
                "root.capability",
                List.of(),
                PermissionDescriptor.of("root.capability.perm", "Root perm", "desc"),
                CommandDescriptor.of("root.capability.cmd", "Root cmd", List.of()),
                ViewDescriptor.of("root.capability.view", "Root view", "root.capability.nav"),
                NavigationContribution.of("root.capability.nav", "Root nav", "root.capability.view", 0));
        Capability leaf = capability(
                "leaf.capability",
                List.of(DependencyDescriptor.of(CapabilityId.of("root.capability"), CapabilityVersion.of("1.0.0"))),
                PermissionDescriptor.of("leaf.capability.perm", "Leaf perm", "desc"),
                CommandDescriptor.of("leaf.capability.cmd", "Leaf cmd", List.of()),
                ViewDescriptor.of("leaf.capability.view", "Leaf view", "leaf.capability.nav"),
                NavigationContribution.of("leaf.capability.nav", "Leaf nav", "leaf.capability.view", 1));

        CapabilityRegistry capabilityRegistry = new CapabilityRegistry();
        CapabilityContributionCatalogs catalogs = new CapabilityContributionCatalogs();
        CapabilityRegistrationService registrationService = new CapabilityRegistrationService(
                capabilityRegistry,
                catalogs,
                new StubPlatformCore(new DefaultCapabilityRegistry(), new DefaultServiceRegistry()),
                new EmptyDocumentEngine());
        CapabilityLifecycleManager lifecycleManager = new CapabilityLifecycleManager(capabilityRegistry, catalogs);
        engine = new DefaultCapabilityEngine(
                new CapabilityDiscovery(List.of(leaf, root)),
                registrationService,
                lifecycleManager,
                capabilityRegistry,
                catalogs);
    }

    @Test
    void discoverRegisterInitializeActivateSequencesDependencyGraph() {
        engine.discoverAndRegisterAll();

        assertEquals(CapabilityLifecycleState.INITIALIZED, engine.stateOf(CapabilityId.of("root.capability")));
        assertEquals(CapabilityLifecycleState.INITIALIZED, engine.stateOf(CapabilityId.of("leaf.capability")));
        CapabilityEngineStatus afterInit = engine.status();
        assertEquals(2, afterInit.discoveredCount());
        assertEquals(2, afterInit.registeredCount());
        assertEquals(0, afterInit.activeCount());

        engine.activateAll();

        assertEquals(CapabilityLifecycleState.ACTIVE, engine.stateOf(CapabilityId.of("root.capability")));
        assertEquals(CapabilityLifecycleState.ACTIVE, engine.stateOf(CapabilityId.of("leaf.capability")));
        assertEquals(2, engine.status().activeCount());
        assertEquals(2, engine.activePermissions().size());
        assertEquals(2, engine.activeCommands().size());
        assertEquals(2, engine.activeViews().size());
        assertEquals(2, engine.activeNavigation().size());
    }

    @Test
    void activeQueriesExcludeDeactivatedCapabilityContributions() {
        engine.discoverAndRegisterAll();
        engine.activateAll();

        engine.deactivate(CapabilityId.of("leaf.capability"));

        assertEquals(CapabilityLifecycleState.DEACTIVATED, engine.stateOf(CapabilityId.of("leaf.capability")));
        assertEquals(1, engine.activePermissions().size());
        assertEquals("root.capability.perm", engine.activePermissions().get(0).permissionId());
        assertEquals(1, engine.activeCommands().size());
        assertEquals(1, engine.activeViews().size());
        assertEquals(1, engine.activeNavigation().size());
        assertEquals(1, engine.status().activeCount());
        assertEquals(2, engine.status().registeredCount());
    }

    @Test
    void statusCountsMatchRegistryAtEachLifecycleStage() {
        assertEquals(new CapabilityEngineStatus(0, 0, 0, 0), engine.status());

        engine.discoverAndRegisterAll();
        assertEquals(new CapabilityEngineStatus(2, 2, 0, 0), engine.status());

        engine.activateAll();
        assertEquals(new CapabilityEngineStatus(2, 2, 2, 0), engine.status());

        engine.stopAll();
        assertEquals(new CapabilityEngineStatus(2, 2, 0, 0), engine.status());
    }

    private static Capability capability(
            String id,
            List<DependencyDescriptor> dependencies,
            PermissionDescriptor permission,
            CommandDescriptor command,
            ViewDescriptor view,
            NavigationContribution navigation) {
        CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
                .id(CapabilityId.of(id))
                .name("Capability " + id)
                .version(CapabilityVersion.of("1.0.0"))
                .description("Facade test capability")
                .dependencies(dependencies)
                .permissions(List.of(permission))
                .commands(List.of(command))
                .views(List.of(view))
                .navigationContributions(List.of(navigation))
                .build();
        return new Capability() {
            @Override
            public CapabilityDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public void onInitialize() {
                // test double: no-op
            }

            @Override
            public void onActivate() {
                // test double: no-op
            }

            @Override
            public void onDeactivate() {
                // test double: no-op
            }

            @Override
            public void onStop() {
                // test double: no-op
            }
        };
    }

    private static final class StubPlatformCore implements PlatformCore {
        private final com.tmp.core.api.CapabilityRegistry capabilityRegistry;
        private final ServiceRegistry serviceRegistry;
        private final EventBus eventBus = new SynchronousEventBus();

        private StubPlatformCore(
                com.tmp.core.api.CapabilityRegistry capabilityRegistry, ServiceRegistry serviceRegistry) {
            this.capabilityRegistry = capabilityRegistry;
            this.serviceRegistry = serviceRegistry;
        }

        @Override
        public void registerComponent(PlatformComponent component) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PlatformRegistry platformRegistry() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceRegistry serviceRegistry() {
            return serviceRegistry;
        }

        @Override
        public com.tmp.core.api.CapabilityRegistry capabilityRegistry() {
            return capabilityRegistry;
        }

        @Override
        public EventBus eventBus() {
            return eventBus;
        }

        @Override
        public PlatformConfiguration configuration() {
            throw new UnsupportedOperationException();
        }

        @Override
        public LifecycleManager lifecycleManager() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PlatformStatus status() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class EmptyDocumentEngine implements DocumentEngine {
        @Override
        public void registerProcessor(DocumentProcessor processor) {
            // no documents in this facade test
        }

        @Override
        public DocumentMetadata createDocument(CreateDocumentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocumentMetadata updateDocument(UpdateDocumentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocumentMetadata postDocument(UUID documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocumentMetadata unpostDocument(UUID documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocumentMetadata closeDocument(UUID documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteDocument(UUID documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DocumentMetadata> findById(UUID documentId) {
            return Optional.empty();
        }

        @Override
        public List<DocumentMetadata> search(DocumentQuery query) {
            return List.of();
        }

        @Override
        public List<DocumentTypeDescriptor> registeredTypes() {
            return List.of();
        }

        @Override
        public DocumentEngineStatus status() {
            throw new UnsupportedOperationException();
        }
    }
}
