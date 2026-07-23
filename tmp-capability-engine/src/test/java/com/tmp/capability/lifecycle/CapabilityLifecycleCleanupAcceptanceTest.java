package com.tmp.capability.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.CapabilityRuntimeAccess;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.DocumentContribution;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.capability.api.PublicServiceContribution;
import com.tmp.capability.api.ViewDescriptor;
import com.tmp.capability.contribution.CapabilityContributionCatalogs;
import com.tmp.capability.contribution.CapabilityExternalContributionRegistry;
import com.tmp.capability.registration.CapabilityRegistrationService;
import com.tmp.capability.registry.CapabilityRegistry;
import com.tmp.core.api.EventBus;
import com.tmp.core.api.LifecycleManager;
import com.tmp.core.api.PlatformConfiguration;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.PlatformRegistry;
import com.tmp.core.api.PlatformStatus;
import com.tmp.core.api.ServiceRegistration;
import com.tmp.core.api.ServiceRegistry;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.core.api.component.PlatformComponentMetadata;
import com.tmp.core.api.event.DomainEvent;
import com.tmp.core.api.event.EventHandler;
import com.tmp.core.api.event.EventSubscription;
import com.tmp.core.api.event.PlatformEvent;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CapabilityLifecycleCleanupAcceptanceTest {

    private CapabilityRegistry capabilityRegistry;
    private CapabilityContributionCatalogs catalogs;
    private CapabilityExternalContributionRegistry externalContributions;
    private CapabilityEventSubscriptionRegistry eventSubscriptions;
    private DefaultCapabilityRegistry platformCapabilityRegistry;
    private ControllableServiceRegistry serviceRegistry;
    private RecordingDocumentEngine documentEngine;
    private ControllableEventBus eventBus;
    private CapabilityRegistrationService registrationService;
    private CapabilityLifecycleManager lifecycle;

    @BeforeEach
    void setUp() {
        capabilityRegistry = new CapabilityRegistry();
        catalogs = new CapabilityContributionCatalogs();
        externalContributions = new CapabilityExternalContributionRegistry();
        eventSubscriptions = new CapabilityEventSubscriptionRegistry();
        platformCapabilityRegistry = new DefaultCapabilityRegistry();
        serviceRegistry = new ControllableServiceRegistry();
        documentEngine = new RecordingDocumentEngine();
        eventBus = new ControllableEventBus();
        PlatformCore platformCore = new StubPlatformCore(platformCapabilityRegistry, serviceRegistry, eventBus);
        registrationService = new CapabilityRegistrationService(
                capabilityRegistry,
                catalogs,
                externalContributions,
                eventSubscriptions,
                platformCore,
                documentEngine);
        lifecycle = new CapabilityLifecycleManager(
                capabilityRegistry, catalogs, externalContributions, eventSubscriptions, platformCore);
    }

    @Test
    void initializeFailureRemovesPublicServiceProcessorPlatformMetadataAndCatalogs() {
        registrationService.register(fullCapability("failing.init", SampleService.class, new SampleServiceImpl(), true, false));
        registrationService.register(
                fullCapability("independent.ok", IndependentService.class, new IndependentServiceImpl(), false, false));

        lifecycle.initializeAll();
        lifecycle.activateAll();

        assertEquals(CapabilityLifecycleState.FAILED, stateOf("failing.init"));
        assertEquals(CapabilityLifecycleState.ACTIVE, stateOf("independent.ok"));
        assertTrue(serviceRegistry.lookup(SampleService.class).isEmpty());
        assertTrue(serviceRegistry.lookup(IndependentService.class).isPresent());
        assertTrue(platformCapabilityRegistry.findById("failing.init").isEmpty());
        assertTrue(platformCapabilityRegistry.findById("independent.ok").isPresent());
        assertTrue(catalogs.activePermissions().stream()
                .noneMatch(permission -> permission.permissionId().startsWith("failing.init")));
        assertTrue(catalogs.activeCommands().stream()
                .noneMatch(command -> command.commandId().startsWith("failing.init")));
        assertTrue(catalogs.activeViews().stream()
                .noneMatch(view -> view.viewId().startsWith("failing.init")));
        assertThrows(
                IllegalStateException.class,
                () -> documentEngine.createDocument(new CreateDocumentCommand("failing.init.document", "blocked")));
        documentEngine.createDocument(new CreateDocumentCommand("independent.ok.document", "allowed"));
    }

    @Test
    void initializeFailureDisablesDocumentProcessorButPreservesExistingDocuments() {
        registrationService.register(fullCapability("failing.doc", SampleService.class, new SampleServiceImpl(), true, false));
        DocumentMetadata created = documentEngine.createDocument(
                new CreateDocumentCommand("failing.doc.document", "before init failure"));

        lifecycle.initializeAll();

        assertEquals(CapabilityLifecycleState.FAILED, stateOf("failing.doc"));
        assertTrue(documentEngine.isDeactivated("failing.doc.document"));
        assertThrows(
                IllegalStateException.class,
                () -> documentEngine.createDocument(new CreateDocumentCommand("failing.doc.document", "blocked")));
        assertTrue(documentEngine.findById(created.id()).isPresent());
    }

    @Test
    void activationFailurePreservesOriginalExceptionAndSuppressesCleanupErrors() {
        registrationService.register(
                fullCapability("failing.activate", SampleService.class, new SampleServiceImpl(), false, true));
        lifecycle.initializeAll();
        serviceRegistry.failNextUnregister = true;
        documentEngine.failNextDeactivate = true;

        lifecycle.activateAll();

        assertEquals(CapabilityLifecycleState.FAILED, stateOf("failing.activate"));
        Throwable cause = lifecycle.failureCause(CapabilityId.of("failing.activate")).orElseThrow();
        assertTrue(cause.getMessage().contains("activate failed"));
        assertTrue(cause.getSuppressed().length >= 1);
        assertTrue(containsMessage(cause, "unregister failed") || containsMessage(cause, "deactivate failed"));
        assertTrue(serviceRegistry.lookup(SampleService.class).isEmpty());
        assertTrue(platformCapabilityRegistry.findById("failing.activate").isEmpty());
        assertTrue(documentEngine.deactivateAttempts.get() >= 1);
        assertTrue(catalogs.activePermissions().isEmpty());
    }

    @Test
    void remainingCleanupStepsExecuteAfterPartialCleanupFailure() {
        registrationService.register(
                fullCapability("partial.cleanup", SampleService.class, new SampleServiceImpl(), false, true));
        lifecycle.initializeAll();
        serviceRegistry.failNextUnregister = true;

        lifecycle.activateAll();

        assertEquals(CapabilityLifecycleState.FAILED, stateOf("partial.cleanup"));
        assertTrue(platformCapabilityRegistry.findById("partial.cleanup").isEmpty());
        assertTrue(documentEngine.isDeactivated("partial.cleanup.document"));
        assertTrue(catalogs.activeCommands().isEmpty());
    }

    @Test
    void deactivationCleanupFailureResultsInFailedNotDeactivatedAndNoStaleService() {
        registrationService.register(
                fullCapability("cleanup.fail", SampleService.class, new SampleServiceImpl(), false, false));
        lifecycle.initializeAll();
        lifecycle.activateAll();
        serviceRegistry.failNextUnregister = true;

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> lifecycle.deactivate(CapabilityId.of("cleanup.fail")));

        assertTrue(containsMessage(failure, "unregister failed"));
        assertEquals(CapabilityLifecycleState.FAILED, stateOf("cleanup.fail"));
        assertTrue(serviceRegistry.lookup(SampleService.class).isEmpty());
        assertTrue(platformCapabilityRegistry.findById("cleanup.fail").isEmpty());
    }

    @Test
    void noStaleSubscriptionAfterSuccessfulCleanup() {
        AtomicBoolean invoked = new AtomicBoolean();
        registrationService.register(subscribingCapability("event.cleanup", invoked));
        lifecycle.initializeAll();
        lifecycle.activateAll();
        eventBus.publish(new TestDomainEvent());
        assertTrue(invoked.get());

        invoked.set(false);
        lifecycle.deactivate(CapabilityId.of("event.cleanup"));
        eventBus.publish(new TestDomainEvent());

        assertFalse(invoked.get());
        assertEquals(CapabilityLifecycleState.DEACTIVATED, stateOf("event.cleanup"));
    }

    @Test
    void unsubscribeFailureIsObservableOnDeactivation() {
        AtomicBoolean invoked = new AtomicBoolean();
        registrationService.register(subscribingCapability("event.fail.unsub", invoked));
        lifecycle.initializeAll();
        lifecycle.activateAll();
        eventBus.failNextUnsubscribe = true;

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> lifecycle.deactivate(CapabilityId.of("event.fail.unsub")));

        assertTrue(containsMessage(failure, "unsubscribe failed"));
        assertEquals(CapabilityLifecycleState.FAILED, stateOf("event.fail.unsub"));
    }

    @Test
    void stopAllContinuesReverseShutdownAfterCleanupFailure() {
        registrationService.register(fullCapability("stop.a", SampleService.class, new SampleServiceImpl(), false, false));
        registrationService.register(
                fullCapability("stop.b", IndependentService.class, new IndependentServiceImpl(), false, false));
        registrationService.register(
                fullCapability("stop.c", ThirdService.class, new ThirdServiceImpl(), false, false));
        lifecycle.initializeAll();
        lifecycle.activateAll();

        eventSubscriptions.runWithCapability(CapabilityId.of("stop.b"), () -> eventSubscriptions.recordSubscription(
                new EventSubscription() {
                    @Override
                    public void unsubscribe() {
                        throw new IllegalStateException("unsubscribe failed for stop.b");
                    }

                    @Override
                    public boolean isActive() {
                        return true;
                    }
                }));

        IllegalStateException failure = assertThrows(IllegalStateException.class, lifecycle::stopAll);

        assertTrue(containsMessage(failure, "unsubscribe failed"));
        assertEquals(CapabilityLifecycleState.STOPPED, stateOf("stop.a"));
        assertEquals(CapabilityLifecycleState.FAILED, stateOf("stop.b"));
        assertEquals(CapabilityLifecycleState.STOPPED, stateOf("stop.c"));
    }

    @Test
    void unsubscribeAllAggregatesFailuresWithoutSilentSwallow() {
        CapabilityId id = CapabilityId.of("unsub.agg");
        eventSubscriptions.runWithCapability(id, () -> {
            eventSubscriptions.recordSubscription(new EventSubscription() {
                @Override
                public void unsubscribe() {
                    throw new IllegalStateException("first unsub");
                }

                @Override
                public boolean isActive() {
                    return true;
                }
            });
            eventSubscriptions.recordSubscription(new EventSubscription() {
                @Override
                public void unsubscribe() {
                    throw new IllegalStateException("second unsub");
                }

                @Override
                public boolean isActive() {
                    return true;
                }
            });
        });

        RuntimeException failure = eventSubscriptions.unsubscribeAll(id);
        assertInstanceOf(IllegalStateException.class, failure);
        assertTrue(failure.getMessage().contains("first unsub"));
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("second unsub"));
    }

    private CapabilityLifecycleState stateOf(String id) {
        return capabilityRegistry.findById(CapabilityId.of(id)).orElseThrow().state();
    }

    private static boolean containsMessage(Throwable failure, String fragment) {
        if (failure.getMessage() != null && failure.getMessage().contains(fragment)) {
            return true;
        }
        for (Throwable suppressed : failure.getSuppressed()) {
            if (containsMessage(suppressed, fragment)) {
                return true;
            }
        }
        return failure.getCause() != null && containsMessage(failure.getCause(), fragment);
    }

    private <T> Capability fullCapability(
            String id,
            Class<T> serviceType,
            T serviceInstance,
            boolean failOnInitialize,
            boolean failOnActivate) {
        CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
                .id(CapabilityId.of(id))
                .name("Capability " + id)
                .version(CapabilityVersion.of("1.0.0"))
                .description("Lifecycle cleanup acceptance")
                .permissions(List.of(PermissionDescriptor.of(id + ".perm", "Perm", "desc")))
                .commands(List.of(CommandDescriptor.of(id + ".cmd", "Cmd", List.of())))
                .views(List.of(ViewDescriptor.of(id + ".view", "View", id + ".nav")))
                .documents(List.of(DocumentContribution.of(
                        id + ".document", "Doc", "desc", processorFor(id + ".document"))))
                .publicServices(List.of(PublicServiceContribution.of(serviceType, serviceInstance)))
                .build();
        return new TrackingCapability(descriptor, failOnInitialize, failOnActivate);
    }

    private Capability subscribingCapability(String id, AtomicBoolean invoked) {
        CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
                .id(CapabilityId.of(id))
                .name("Capability " + id)
                .version(CapabilityVersion.of("1.0.0"))
                .description("Subscription cleanup acceptance")
                .commands(List.of(CommandDescriptor.of(id + ".cmd", "Cmd", List.of())))
                .build();
        return new Capability() {
            @Override
            public CapabilityDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public void onInitialize() {
            }

            @Override
            public void onActivate() {
                CapabilityRuntimeAccess.eventBus(
                                new StubPlatformCore(platformCapabilityRegistry, serviceRegistry, eventBus))
                        .subscribeDomain(TestDomainEvent.class, event -> invoked.set(true));
            }

            @Override
            public void onDeactivate() {
            }

            @Override
            public void onStop() {
            }
        };
    }

    private static DocumentProcessor processorFor(String typeId) {
        return new DocumentProcessor() {
            @Override
            public String documentTypeId() {
                return typeId;
            }

            @Override
            public void validateCreate(DocumentOperationContext context) {
            }

            @Override
            public void validateUpdate(DocumentOperationContext context) {
            }

            @Override
            public void onPost(DocumentOperationContext context) {
            }

            @Override
            public void onUnpost(DocumentOperationContext context) {
            }

            @Override
            public void onClose(DocumentOperationContext context) {
            }

            @Override
            public void onDelete(DocumentOperationContext context) {
            }
        };
    }

    private static final class TrackingCapability implements Capability {
        private final CapabilityDescriptor descriptor;
        private final boolean failOnInitialize;
        private final boolean failOnActivate;

        private TrackingCapability(
                CapabilityDescriptor descriptor, boolean failOnInitialize, boolean failOnActivate) {
            this.descriptor = descriptor;
            this.failOnInitialize = failOnInitialize;
            this.failOnActivate = failOnActivate;
        }

        @Override
        public CapabilityDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public void onInitialize() {
            if (failOnInitialize) {
                throw new IllegalStateException("initialize failed for " + descriptor.id());
            }
        }

        @Override
        public void onActivate() {
            if (failOnActivate) {
                throw new IllegalStateException("activate failed for " + descriptor.id());
            }
        }

        @Override
        public void onDeactivate() {
        }

        @Override
        public void onStop() {
        }
    }

    interface SampleService {
        String name();
    }

    interface IndependentService {
        String name();
    }

    interface ThirdService {
        String name();
    }

    static final class SampleServiceImpl implements SampleService {
        @Override
        public String name() {
            return "sample";
        }
    }

    static final class IndependentServiceImpl implements IndependentService {
        @Override
        public String name() {
            return "independent";
        }
    }

    static final class ThirdServiceImpl implements ThirdService {
        @Override
        public String name() {
            return "third";
        }
    }

    private record TestDomainEvent() implements DomainEvent {
        @Override
        public String eventId() {
            return "test-event";
        }

        @Override
        public java.time.Instant occurredAt() {
            return java.time.Instant.now();
        }

        @Override
        public String eventType() {
            return "test.domain";
        }

        @Override
        public String sourceCapabilityId() {
            return "event.cleanup";
        }
    }

    private static final class ControllableServiceRegistry implements ServiceRegistry {
        private final DefaultServiceRegistry delegate = new DefaultServiceRegistry();
        private boolean failNextUnregister;

        @Override
        public <T> ServiceRegistration register(Class<T> serviceType, T instance, PlatformComponentMetadata owner) {
            ServiceRegistration real = delegate.register(serviceType, instance, owner);
            return new ServiceRegistration() {
                @Override
                public Class<?> serviceType() {
                    return real.serviceType();
                }

                @Override
                public PlatformComponentMetadata owner() {
                    return real.owner();
                }

                @Override
                public void unregister() {
                    real.unregister();
                    if (failNextUnregister) {
                        failNextUnregister = false;
                        throw new IllegalStateException("unregister failed");
                    }
                }
            };
        }

        @Override
        public <T> Optional<T> lookup(Class<T> serviceType) {
            return delegate.lookup(serviceType);
        }

        @Override
        public <T> List<T> lookupAll(Class<T> serviceType) {
            return delegate.lookupAll(serviceType);
        }

        @Override
        public List<PlatformComponentMetadata> registeredServices() {
            return delegate.registeredServices();
        }

        @Override
        public int registeredServiceCount() {
            return delegate.registeredServiceCount();
        }
    }

    private static final class ControllableEventBus implements EventBus {
        private final SynchronousEventBus delegate = new SynchronousEventBus();
        private boolean failNextUnsubscribe;

        @Override
        public void publish(PlatformEvent event) {
            delegate.publish(event);
        }

        @Override
        public void publish(DomainEvent event) {
            delegate.publish(event);
        }

        @Override
        public EventSubscription subscribePlatform(
                Class<? extends PlatformEvent> eventType, EventHandler<PlatformEvent> handler) {
            return wrap(delegate.subscribePlatform(eventType, handler));
        }

        @Override
        public EventSubscription subscribeDomain(
                Class<? extends DomainEvent> eventType, EventHandler<DomainEvent> handler) {
            return wrap(delegate.subscribeDomain(eventType, handler));
        }

        private EventSubscription wrap(EventSubscription real) {
            return new EventSubscription() {
                @Override
                public void unsubscribe() {
                    real.unsubscribe();
                    if (failNextUnsubscribe) {
                        failNextUnsubscribe = false;
                        throw new IllegalStateException("unsubscribe failed");
                    }
                }

                @Override
                public boolean isActive() {
                    return real.isActive();
                }
            };
        }
    }

    private static final class RecordingDocumentEngine implements DocumentEngine {
        private final List<DocumentTypeDescriptor> types = new ArrayList<>();
        private final List<DocumentMetadata> documents = new ArrayList<>();
        private final Set<String> deactivated = new HashSet<>();
        private boolean failNextDeactivate;
        private final AtomicInteger deactivateAttempts = new AtomicInteger();

        boolean isDeactivated(String typeId) {
            return deactivated.contains(typeId);
        }

        @Override
        public DocumentProcessorRegistration registerProcessor(DocumentProcessor processor) {
            String typeId = processor.documentTypeId();
            for (DocumentTypeDescriptor existing : types) {
                if (existing.typeId().equals(typeId)) {
                    throw new IllegalStateException("duplicate processor: " + typeId);
                }
            }
            types.add(new DocumentTypeDescriptor(typeId, typeId, "registered"));
            deactivated.remove(typeId);
            return new DocumentProcessorRegistration() {
                @Override
                public String documentTypeId() {
                    return typeId;
                }

                @Override
                public void unregister() {
                    types.removeIf(type -> type.typeId().equals(typeId));
                    deactivated.remove(typeId);
                }

                @Override
                public void deactivate() {
                    deactivateAttempts.incrementAndGet();
                    deactivated.add(typeId);
                    types.removeIf(type -> type.typeId().equals(typeId));
                    if (failNextDeactivate) {
                        failNextDeactivate = false;
                        throw new IllegalStateException("deactivate failed");
                    }
                }
            };
        }

        @Override
        public DocumentMetadata createDocument(CreateDocumentCommand command) {
            if (deactivated.contains(command.documentTypeId())
                    || types.stream().noneMatch(type -> type.typeId().equals(command.documentTypeId()))) {
                throw new IllegalStateException(
                        "Document processor deactivated for type: " + command.documentTypeId());
            }
            DocumentMetadata metadata = new DocumentMetadata(
                    UUID.randomUUID(),
                    command.documentTypeId(),
                    "DOC-" + documents.size(),
                    command.title(),
                    com.tmp.document.api.DocumentStatus.DRAFT,
                    0L,
                    java.time.Instant.now(),
                    java.time.Instant.now(),
                    null,
                    null);
            documents.add(metadata);
            return metadata;
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
            return documents.stream().filter(document -> document.id().equals(documentId)).findFirst();
        }

        @Override
        public List<DocumentMetadata> search(DocumentQuery query) {
            return List.copyOf(documents);
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

    private static final class StubPlatformCore implements PlatformCore {
        private final com.tmp.core.api.CapabilityRegistry capabilityRegistry;
        private final ServiceRegistry serviceRegistry;
        private final EventBus eventBus;

        private StubPlatformCore(
                com.tmp.core.api.CapabilityRegistry capabilityRegistry,
                ServiceRegistry serviceRegistry,
                EventBus eventBus) {
            this.capabilityRegistry = capabilityRegistry;
            this.serviceRegistry = serviceRegistry;
            this.eventBus = eventBus;
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
}
