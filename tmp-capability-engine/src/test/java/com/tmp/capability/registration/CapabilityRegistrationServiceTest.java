package com.tmp.capability.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.DocumentContribution;
import com.tmp.capability.api.EventContribution;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.capability.api.PublicServiceContribution;
import com.tmp.capability.api.SettingsContribution;
import com.tmp.capability.api.ViewDescriptor;
import com.tmp.capability.contribution.CapabilityContributionCatalogs;
import com.tmp.capability.contribution.CapabilityExternalContributionRegistry;
import com.tmp.capability.lifecycle.CapabilityEventSubscriptionRegistry;
import com.tmp.capability.registry.CapabilityRegistry;
import com.tmp.core.api.EventBus;
import com.tmp.core.api.ServiceRegistration;
import com.tmp.core.api.LifecycleManager;
import com.tmp.core.api.PlatformConfiguration;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.PlatformRegistry;
import com.tmp.core.api.PlatformStatus;
import com.tmp.core.api.ServiceRegistry;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.core.api.component.PlatformComponentMetadata;
import com.tmp.core.event.SynchronousEventBus;
import com.tmp.core.registry.DefaultCapabilityRegistry;
import com.tmp.core.registry.DefaultServiceRegistry;
import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentEngineStatus;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentTypeDescriptor;
import com.tmp.document.api.UpdateDocumentCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CapabilityRegistrationServiceTest {

    private CapabilityRegistry capabilityRegistry;
    private CapabilityContributionCatalogs catalogs;
    private DefaultCapabilityRegistry platformCapabilityRegistry;
    private DefaultServiceRegistry serviceRegistry;
    private CapabilityExternalContributionRegistry externalContributions;
    private CapabilityEventSubscriptionRegistry eventSubscriptions;
    private RecordingDocumentEngine documentEngine;
    private CapabilityRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        capabilityRegistry = new CapabilityRegistry();
        catalogs = new CapabilityContributionCatalogs();
        platformCapabilityRegistry = new DefaultCapabilityRegistry();
        serviceRegistry = new DefaultServiceRegistry();
        externalContributions = new CapabilityExternalContributionRegistry();
        eventSubscriptions = new CapabilityEventSubscriptionRegistry();
        documentEngine = new RecordingDocumentEngine();
        registrationService = new CapabilityRegistrationService(
                capabilityRegistry,
                catalogs,
                externalContributions,
                eventSubscriptions,
                new StubPlatformCore(platformCapabilityRegistry, serviceRegistry),
                documentEngine);
    }

    @Test
    void happyPathRegistersAllContributionCategories() {
        Capability capability = fullCapability("sample.capability");

        registrationService.register(capability);

        assertTrue(capabilityRegistry.findById(CapabilityId.of("sample.capability")).isPresent());
        assertEquals(
                CapabilityLifecycleState.REGISTERED,
                capabilityRegistry.findById(CapabilityId.of("sample.capability")).orElseThrow().state());
        assertEquals(1, catalogs.activePermissions().size());
        assertEquals(1, catalogs.activeCommands().size());
        assertEquals(1, catalogs.activeViews().size());
        assertEquals(1, catalogs.activeNavigation().size());
        assertEquals(1, catalogs.activeSettings().size());
        assertEquals(1, catalogs.activeEvents().size());
        assertTrue(platformCapabilityRegistry.findById("sample.capability").isPresent());
        assertEquals(1, documentEngine.registeredTypes().size());
        assertTrue(serviceRegistry.lookup(SampleService.class).isPresent());
    }

    @Test
    void failureAtInternalCatalogRollsBackReservationOnly() {
        registrationService.register(fullCapability("owner.a"));

        Capability conflicting = capabilityWithPermissionOnly(
                "owner.b", PermissionDescriptor.of("owner.a.perm", "Conflict", "desc"));

        CapabilityRegistrationException failure =
                assertThrows(CapabilityRegistrationException.class, () -> registrationService.register(conflicting));

        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(capabilityRegistry.findById(CapabilityId.of("owner.b")).isEmpty());
        assertTrue(catalogs.permissions().ownerOf("owner.b.perm").isEmpty());
        assertTrue(catalogs.commands().ownerOf("owner.b.cmd").isEmpty());
        // reservation released — retry of same id with a non-conflicting descriptor succeeds
        registrationService.register(capabilityWithPermissionOnly(
                "owner.b", PermissionDescriptor.of("owner.b.perm", "Perm", "desc")));
        assertTrue(capabilityRegistry.findById(CapabilityId.of("owner.b")).isPresent());
    }

    @Test
    void failureAtDocumentContributionPreCheckRollsBackCatalogsAndReservation() {
        documentEngine.seedType("sample.document");

        CapabilityRegistrationException failure = assertThrows(
                CapabilityRegistrationException.class,
                () -> registrationService.register(fullCapability("sample.capability")));

        assertTrue(failure.getCause().getMessage().contains("sample.document"));
        assertTrue(capabilityRegistry.findById(CapabilityId.of("sample.capability")).isEmpty());
        assertTrue(catalogs.activePermissions().isEmpty());
        assertTrue(platformCapabilityRegistry.findById("sample.capability").isEmpty());
        assertEquals(1, documentEngine.registeredTypes().size());
        assertEquals(0, documentEngine.registerProcessorCallCount());
    }

    @Test
    void duplicateDocumentProcessorRejectedWithNoPartialDocumentRegistration() {
        documentEngine.failOnRegisterProcessor = true;

        CapabilityRegistrationException failure = assertThrows(
                CapabilityRegistrationException.class,
                () -> registrationService.register(fullCapability("sample.capability")));

        assertTrue(failure.getCause().getMessage().contains("processor rejected"));
        assertTrue(capabilityRegistry.findById(CapabilityId.of("sample.capability")).isEmpty());
        assertTrue(catalogs.activePermissions().isEmpty());
        assertEquals(0, documentEngine.registeredTypes().size());
    }

    @Test
    void failureAtServiceContributionRollsBackCapabilityEngineOwnedState() {
        FailingServiceRegistry failingServices = new FailingServiceRegistry();
        CapabilityRegistrationService service = new CapabilityRegistrationService(
                capabilityRegistry,
                catalogs,
                externalContributions,
                eventSubscriptions,
                new StubPlatformCore(platformCapabilityRegistry, failingServices),
                documentEngine);

        CapabilityRegistrationException failure = assertThrows(
                CapabilityRegistrationException.class,
                () -> service.register(fullCapability("sample.capability")));

        assertTrue(failure.getCause().getMessage().contains("service registration failed"));
        assertTrue(capabilityRegistry.findById(CapabilityId.of("sample.capability")).isEmpty());
        assertTrue(catalogs.activePermissions().isEmpty());
        assertEquals(0, documentEngine.registeredTypes().size());
        assertTrue(platformCapabilityRegistry.findById("sample.capability").isEmpty());
    }

    @Test
    void retryAfterCorrectedFailureSucceeds() {
        documentEngine.seedType("sample.document");
        assertThrows(
                CapabilityRegistrationException.class,
                () -> registrationService.register(fullCapability("sample.capability")));

        documentEngine.clearSeededTypes();
        registrationService.register(fullCapability("sample.capability"));

        assertTrue(capabilityRegistry.findById(CapabilityId.of("sample.capability")).isPresent());
        assertEquals(1, catalogs.activePermissions().size());
    }

    @Test
    void concurrentRegistrationOfSameIdHasExactlyOneWinner() throws Exception {
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        CyclicBarrier gate = new CyclicBarrier(2);

        Runnable attempt = () -> {
            awaitGate(gate);
            try {
                registrationService.register(fullCapability("concurrent.capability"));
                successes.incrementAndGet();
            } catch (CapabilityRegistrationException ignored) {
                failures.incrementAndGet();
            }
        };

        Thread first = new Thread(attempt);
        Thread second = new Thread(attempt);
        first.start();
        second.start();
        first.join(TimeUnit.SECONDS.toMillis(10));
        second.join(TimeUnit.SECONDS.toMillis(10));

        assertEquals(1, successes.get());
        assertEquals(1, failures.get());
        assertEquals(1, capabilityRegistry.findAll().size());
        assertEquals(1, catalogs.activePermissions().size());
    }

    @Test
    void failureAtPlatformCapabilityRollsBackAllPriorState() {
        platformCapabilityRegistry.register(
                new com.tmp.core.api.capability.CapabilityDescriptor("sample.capability", "Existing", "1.0.0"));

        CapabilityRegistrationException failure = assertThrows(
                CapabilityRegistrationException.class,
                () -> registrationService.register(fullCapability("sample.capability")));

        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(capabilityRegistry.findById(CapabilityId.of("sample.capability")).isEmpty());
        assertTrue(catalogs.activePermissions().isEmpty());
        assertEquals(0, documentEngine.registeredTypes().size());
    }

    @Test
    void compensationFailuresAreSuppressedOnOriginalRegistrationFailure() {
        documentEngine.failOnUnregister = true;
        FailingServiceRegistry failingServices = new FailingServiceRegistry();
        CapabilityRegistrationService service = new CapabilityRegistrationService(
                capabilityRegistry,
                catalogs,
                externalContributions,
                eventSubscriptions,
                new StubPlatformCore(platformCapabilityRegistry, failingServices),
                documentEngine);

        CapabilityRegistrationException failure = assertThrows(
                CapabilityRegistrationException.class, () -> service.register(fullCapability("sample.capability")));

        assertTrue(failure.getCause().getMessage().contains("service registration failed"));
        assertEquals(1, failure.getCause().getSuppressed().length);
        assertTrue(capabilityRegistry.findById(CapabilityId.of("sample.capability")).isEmpty());
        assertTrue(platformCapabilityRegistry.findById("sample.capability").isEmpty());
    }

    @Test
    void originalExceptionIsPreservedAndInspectableAfterFailure() {
        documentEngine.seedType("sample.document");

        CapabilityRegistrationException failure = assertThrows(
                CapabilityRegistrationException.class,
                () -> registrationService.register(fullCapability("sample.capability")));

        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("Document type already registered"));
    }

    private static Capability fullCapability(String id) {
        SampleServiceImpl service = new SampleServiceImpl();
        CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
                .id(CapabilityId.of(id))
                .name("Capability " + id)
                .version(CapabilityVersion.of("1.0.0"))
                .description("Full test capability")
                .permissions(List.of(PermissionDescriptor.of(id + ".perm", "Perm", "desc")))
                .commands(List.of(CommandDescriptor.of(id + ".cmd", "Cmd", List.of())))
                .views(List.of(ViewDescriptor.of(id + ".view", "View", id + ".nav")))
                .navigationContributions(List.of(NavigationContribution.of(id + ".nav", "Nav", id + ".view", 0)))
                .settings(List.of(SettingsContribution.of(id + ".setting", "Setting", "desc", "0")))
                .events(List.of(EventContribution.of(id + ".event", "Event")))
                .documents(List.of(DocumentContribution.of(
                        "sample.document", "Sample document", "desc", processorFor("sample.document"))))
                .publicServices(List.of(PublicServiceContribution.of(SampleService.class, service)))
                .build();
        return wrap(descriptor);
    }

    private static Capability capabilityWithPermissionOnly(String id, PermissionDescriptor permission) {
        CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
                .id(CapabilityId.of(id))
                .name("Capability " + id)
                .version(CapabilityVersion.of("1.0.0"))
                .description("Partial capability")
                .permissions(List.of(permission))
                .commands(List.of(CommandDescriptor.of(id + ".cmd", "Cmd", List.of())))
                .build();
        return wrap(descriptor);
    }

    private static Capability wrap(CapabilityDescriptor descriptor) {
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

    private static DocumentProcessor processorFor(String documentTypeId) {
        return new DocumentProcessor() {
            @Override
            public String documentTypeId() {
                return documentTypeId;
            }

            @Override
            public void validateCreate(DocumentOperationContext context) {
                // test double: no-op
            }

            @Override
            public void validateUpdate(DocumentOperationContext context) {
                // test double: no-op
            }

            @Override
            public void onPost(DocumentOperationContext context) {
                // test double: no-op
            }

            @Override
            public void onUnpost(DocumentOperationContext context) {
                // test double: no-op
            }

            @Override
            public void onClose(DocumentOperationContext context) {
                // test double: no-op
            }

            @Override
            public void onDelete(DocumentOperationContext context) {
                // test double: no-op
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

    interface SampleService {
        String name();
    }

    static final class SampleServiceImpl implements SampleService {
        @Override
        public String name() {
            return "sample";
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
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public PlatformRegistry platformRegistry() {
            throw new UnsupportedOperationException("not used in this test");
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
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public LifecycleManager lifecycleManager() {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public PlatformStatus status() {
            throw new UnsupportedOperationException("not used in this test");
        }
    }

    private static final class FailingServiceRegistry implements ServiceRegistry {
        @Override
        public <T> ServiceRegistration register(Class<T> serviceType, T instance, PlatformComponentMetadata owner) {
            throw new IllegalStateException("service registration failed");
        }

        @Override
        public <T> Optional<T> lookup(Class<T> serviceType) {
            return Optional.empty();
        }

        @Override
        public <T> List<T> lookupAll(Class<T> serviceType) {
            return List.of();
        }

        @Override
        public List<PlatformComponentMetadata> registeredServices() {
            return List.of();
        }

        @Override
        public int registeredServiceCount() {
            return 0;
        }
    }

    private static final class RecordingDocumentEngine implements DocumentEngine {
        private final List<DocumentTypeDescriptor> types = new ArrayList<>();
        private final AtomicInteger registerProcessorCalls = new AtomicInteger();
        private boolean failOnRegisterProcessor;
        private boolean failOnUnregister;

        void seedType(String typeId) {
            types.add(new DocumentTypeDescriptor(typeId, typeId, "seeded"));
        }

        void clearSeededTypes() {
            types.clear();
        }

        int registerProcessorCallCount() {
            return registerProcessorCalls.get();
        }

        @Override
        public DocumentProcessorRegistration registerProcessor(DocumentProcessor processor) {
            registerProcessorCalls.incrementAndGet();
            if (failOnRegisterProcessor) {
                throw new IllegalStateException("processor rejected");
            }
            for (DocumentTypeDescriptor existing : types) {
                if (existing.typeId().equals(processor.documentTypeId())) {
                    throw new IllegalStateException("duplicate processor: " + processor.documentTypeId());
                }
            }
            String typeId = processor.documentTypeId();
            types.add(new DocumentTypeDescriptor(typeId, typeId, "registered"));
            return new DocumentProcessorRegistration() {
                @Override
                public String documentTypeId() {
                    return typeId;
                }

                @Override
                public void unregister() {
                    if (failOnUnregister) {
                        throw new IllegalStateException("unregister compensation failed");
                    }
                    types.removeIf(type -> type.typeId().equals(typeId));
                }

                @Override
                public void deactivate() {
                    // test double: no-op
                }
            };
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
            return List.copyOf(types);
        }

        @Override
        public DocumentEngineStatus status() {
            throw new UnsupportedOperationException();
        }
    }
}
