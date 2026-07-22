package com.tmp.capability.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.DefaultCapabilityEngine;
import com.tmp.capability.discovery.CapabilityDiscovery;
import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.capability.contribution.CapabilityContributionCatalogs;
import com.tmp.capability.registration.CapabilityRegistrationException;
import com.tmp.capability.registration.CapabilityRegistrationService;
import com.tmp.capability.registry.CapabilityRegistry;
import com.tmp.core.api.EventBus;
import com.tmp.core.api.LifecycleManager;
import com.tmp.core.api.PlatformConfiguration;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.PlatformStatus;
import com.tmp.core.api.PlatformRegistry;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CapabilityLifecycleConcurrencyTest {

    private static final int CONCURRENT_REGISTRATION_THREADS = 8;
    private static final int ACTIVATION_RACE_ITERATIONS = 200;
    private static final int SNAPSHOT_READER_ITERATIONS = 500;

    @Test
    void concurrentRegistrationOfSameCapabilityIdHasExactlyOneWinner() throws Exception {
        TestFixture fixture = TestFixture.create();
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        CyclicBarrier gate = new CyclicBarrier(CONCURRENT_REGISTRATION_THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REGISTRATION_THREADS);

        try {
            for (int i = 0; i < CONCURRENT_REGISTRATION_THREADS; i++) {
                executor.submit(() -> {
                    awaitGate(gate);
                    try {
                        fixture.registrationService().register(minimalCapability("race.capability"));
                        successes.incrementAndGet();
                    } catch (CapabilityRegistrationException ignored) {
                        failures.incrementAndGet();
                    }
                });
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, successes.get());
        assertEquals(CONCURRENT_REGISTRATION_THREADS - 1, failures.get());
        assertEquals(1, fixture.capabilityRegistry().findAll().size());
        assertEquals(1, fixture.catalogs().activePermissions().size());
    }

    @Test
    void activateVersusDeactivateRaceDoesNotLeakPartialState() throws Exception {
        for (int iteration = 0; iteration < ACTIVATION_RACE_ITERATIONS; iteration++) {
            TestFixture fixture = TestFixture.create();
            CapabilityId id = CapabilityId.of("race.capability." + iteration);
            fixture.registrationService().register(minimalCapability(id.value()));
            fixture.lifecycleManager().initializeAll();

            CyclicBarrier gate = new CyclicBarrier(2);
            AtomicReference<Throwable> unexpected = new AtomicReference<>();

            Thread activator = new Thread(() -> {
                awaitGate(gate);
                try {
                    fixture.lifecycleManager().activateAll();
                } catch (RuntimeException failure) {
                    unexpected.compareAndSet(null, failure);
                }
            });
            Thread deactivator = new Thread(() -> {
                awaitGate(gate);
                try {
                    fixture.lifecycleManager().deactivate(id);
                } catch (RuntimeException ignored) {
                    // Expected when activation has not completed yet.
                }
            });

            activator.start();
            deactivator.start();
            activator.join(TimeUnit.SECONDS.toMillis(5));
            deactivator.join(TimeUnit.SECONDS.toMillis(5));

            assertEquals(null, unexpected.get(), "activateAll must not throw during race");
            assertRegistryConsistent(fixture);
            assertActiveCapabilitiesHaveCompleteContributions(fixture.engine(), fixture.catalogs());
        }
    }

    @Test
    void readOnlySnapshotsRemainConsistentDuringLifecycleChanges() throws Exception {
        TestFixture fixture = TestFixture.create();
        fixture.registrationService().register(minimalCapability("seed.capability"));
        fixture.lifecycleManager().initializeAll();
        fixture.lifecycleManager().activateAll();

        CountDownLatch writerDone = new CountDownLatch(1);
        AtomicReference<Throwable> readerFailure = new AtomicReference<>();
        final int writerIterations = 50;

        Thread reader = new Thread(() -> {
            try {
                for (int i = 0; i < SNAPSHOT_READER_ITERATIONS; i++) {
                    assertSnapshotConsistent(fixture.engine());
                    assertActiveCapabilitiesHaveCompleteContributions(fixture.engine(), fixture.catalogs());
                }
            } catch (Throwable failure) {
                readerFailure.compareAndSet(null, failure);
            }
        });

        Thread writer = new Thread(() -> {
            try {
                for (int index = 0; index < writerIterations; index++) {
                    CapabilityId id = CapabilityId.of("writer.capability." + index);
                    try {
                        fixture.registrationService().register(minimalCapability(id.value()));
                        fixture.lifecycleManager().initializeAll();
                        fixture.lifecycleManager().activateAll();
                        if (index % 2 == 0) {
                            fixture.lifecycleManager().deactivate(id);
                        }
                    } catch (RuntimeException ignored) {
                        // duplicate ids or lifecycle races are acceptable; invariants are checked by the reader.
                    }
                }
            } catch (Throwable failure) {
                readerFailure.compareAndSet(null, failure);
            } finally {
                writerDone.countDown();
            }
        });

        reader.start();
        writer.start();
        writer.join(TimeUnit.SECONDS.toMillis(10));
        reader.join(TimeUnit.SECONDS.toMillis(10));

        assertEquals(null, readerFailure.get(), "reader must not observe torn snapshots or CME");
        assertRegistryConsistent(fixture);
    }

    private static void assertRegistryConsistent(TestFixture fixture) {
        List<CapabilityId> ids = fixture.capabilityRegistry().findAll().stream()
                .map(registration -> registration.descriptor().id())
                .toList();
        assertEquals(new HashSet<>(ids).size(), ids.size());
        for (CapabilityId id : ids) {
            assertTrue(fixture.capabilityRegistry().findById(id).isPresent());
        }
    }

    private static void assertSnapshotConsistent(CapabilityEngine engine) {
        List<CapabilityDescriptor> snapshot = engine.registeredCapabilities();
        Set<CapabilityId> ids = ConcurrentHashMap.newKeySet();
        for (CapabilityDescriptor descriptor : snapshot) {
            assertTrue(ids.add(descriptor.id()), "snapshot must not contain duplicate capability ids");
            CapabilityLifecycleState state = engine.stateOf(descriptor.id());
            assertFalse(
                    state == CapabilityLifecycleState.ACTIVE && descriptor.commands().isEmpty(),
                    "ACTIVE capability must expose declared commands in descriptor snapshot");
        }

        Set<String> activeCommandIds = new HashSet<>();
        for (CommandDescriptor command : engine.activeCommands()) {
            assertTrue(activeCommandIds.add(command.commandId()), "activeCommands snapshot must not repeat ids");
            boolean ownedByActive = snapshot.stream()
                    .filter(descriptor -> engine.stateOf(descriptor.id()) == CapabilityLifecycleState.ACTIVE)
                    .flatMap(descriptor -> descriptor.commands().stream())
                    .anyMatch(descriptorCommand -> descriptorCommand.commandId().equals(command.commandId()));
            assertTrue(ownedByActive, "active command must belong to an ACTIVE capability descriptor");
        }
    }

    private static void assertActiveCapabilitiesHaveCompleteContributions(
            CapabilityEngine engine, CapabilityContributionCatalogs catalogs) {
        for (CapabilityDescriptor descriptor : engine.registeredCapabilities()) {
            if (engine.stateOf(descriptor.id()) != CapabilityLifecycleState.ACTIVE) {
                continue;
            }
            for (PermissionDescriptor permission : descriptor.permissions()) {
                assertEquals(
                        descriptor.id(),
                        catalogs.permissions().ownerOf(permission.permissionId()).orElseThrow());
            }
            for (CommandDescriptor command : descriptor.commands()) {
                assertEquals(
                        descriptor.id(),
                        catalogs.commands().ownerOf(command.commandId()).orElseThrow());
            }
        }
    }

    private static Capability minimalCapability(String id) {
        CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
                .id(CapabilityId.of(id))
                .name("Capability " + id)
                .version(CapabilityVersion.of("1.0.0"))
                .description("Concurrency test capability")
                .permissions(List.of(PermissionDescriptor.of(id + ".perm", "Perm", "desc")))
                .commands(List.of(CommandDescriptor.of(id + ".cmd", "Cmd", List.of())))
                .build();
        return new Capability() {
            @Override
            public CapabilityDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public void onInitialize() {
                // test double
            }

            @Override
            public void onActivate() {
                // test double
            }

            @Override
            public void onDeactivate() {
                // test double
            }

            @Override
            public void onStop() {
                // test double
            }
        };
    }

    private static void awaitGate(CyclicBarrier gate) {
        try {
            gate.await(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("Concurrent test gate failed", failure);
        }
    }

    private static final class TestFixture {
        private final CapabilityRegistry capabilityRegistry;
        private final CapabilityContributionCatalogs catalogs;
        private final CapabilityRegistrationService registrationService;
        private final CapabilityLifecycleManager lifecycleManager;
        private final DefaultCapabilityEngine engine;

        private TestFixture(
                CapabilityRegistry capabilityRegistry,
                CapabilityContributionCatalogs catalogs,
                CapabilityRegistrationService registrationService,
                CapabilityLifecycleManager lifecycleManager,
                DefaultCapabilityEngine engine) {
            this.capabilityRegistry = capabilityRegistry;
            this.catalogs = catalogs;
            this.registrationService = registrationService;
            this.lifecycleManager = lifecycleManager;
            this.engine = engine;
        }

        static TestFixture create() {
            CapabilityRegistry capabilityRegistry = new CapabilityRegistry();
            CapabilityContributionCatalogs catalogs = new CapabilityContributionCatalogs();
            CapabilityRegistrationService registrationService = new CapabilityRegistrationService(
                    capabilityRegistry,
                    catalogs,
                    new StubPlatformCore(new DefaultCapabilityRegistry(), new DefaultServiceRegistry()),
                    new EmptyDocumentEngine());
            CapabilityLifecycleManager lifecycleManager = new CapabilityLifecycleManager(capabilityRegistry, catalogs);
            DefaultCapabilityEngine engine = new DefaultCapabilityEngine(
                    new CapabilityDiscovery(List.of()),
                    registrationService,
                    lifecycleManager,
                    capabilityRegistry,
                    catalogs);
            return new TestFixture(
                    capabilityRegistry, catalogs, registrationService, lifecycleManager, engine);
        }

        CapabilityRegistry capabilityRegistry() {
            return capabilityRegistry;
        }

        CapabilityContributionCatalogs catalogs() {
            return catalogs;
        }

        CapabilityRegistrationService registrationService() {
            return registrationService;
        }

        CapabilityLifecycleManager lifecycleManager() {
            return lifecycleManager;
        }

        DefaultCapabilityEngine engine() {
            return engine;
        }
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
            // no documents in concurrency tests
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
