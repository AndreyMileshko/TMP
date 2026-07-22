package com.tmp.capability.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.CapabilityRuntimeAccess;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.DocumentContribution;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.capability.api.PublicServiceContribution;
import com.tmp.capability.contribution.CapabilityContributionCatalogs;
import com.tmp.capability.contribution.CapabilityExternalContributionRegistry;
import com.tmp.capability.discovery.CapabilityDiscovery;
import com.tmp.capability.DefaultCapabilityEngine;
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
import com.tmp.core.api.event.DomainEvent;
import com.tmp.core.api.event.EventHandler;
import com.tmp.core.api.event.EventSubscription;
import com.tmp.core.event.SynchronousEventBus;
import com.tmp.core.registry.DefaultCapabilityRegistry;
import com.tmp.core.registry.DefaultServiceRegistry;
import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentEngineStatus;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentTypeDescriptor;
import com.tmp.document.api.UpdateDocumentCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CapabilityDeactivationAcceptanceTest {

    private CapabilityRegistry capabilityRegistry;
    private CapabilityContributionCatalogs catalogs;
    private CapabilityExternalContributionRegistry externalContributions;
    private CapabilityEventSubscriptionRegistry eventSubscriptions;
    private DefaultCapabilityRegistry platformCapabilityRegistry;
    private DefaultServiceRegistry serviceRegistry;
    private SynchronousEventBus eventBus;
    private RecordingDocumentEngine documentEngine;
    private DefaultCapabilityEngine engine;

    @BeforeEach
    void setUp() {
        capabilityRegistry = new CapabilityRegistry();
        catalogs = new CapabilityContributionCatalogs();
        externalContributions = new CapabilityExternalContributionRegistry();
        eventSubscriptions = new CapabilityEventSubscriptionRegistry();
        platformCapabilityRegistry = new DefaultCapabilityRegistry();
        serviceRegistry = new DefaultServiceRegistry();
        eventBus = new SynchronousEventBus();
        documentEngine = new RecordingDocumentEngine();
        PlatformCore platformCore = new StubPlatformCore(platformCapabilityRegistry, serviceRegistry, eventBus);
        CapabilityRegistrationService registrationService = new CapabilityRegistrationService(
                capabilityRegistry,
                catalogs,
                externalContributions,
                eventSubscriptions,
                platformCore,
                documentEngine);
        CapabilityLifecycleManager lifecycleManager = new CapabilityLifecycleManager(
                capabilityRegistry, catalogs, externalContributions, eventSubscriptions, platformCore);
        engine = new DefaultCapabilityEngine(
                new CapabilityDiscovery(List.of(testCapability())),
                registrationService,
                lifecycleManager,
                capabilityRegistry,
                catalogs);
    }

    @Test
    void deactivationRemovesExternalContributionsButPreservesExistingDocuments() {
        engine.discoverAndRegisterAll();
        engine.activateAll();
        DocumentMetadata created = documentEngine.createDocument(
                new CreateDocumentCommand("sample.document", "persisted"));

        engine.deactivate(CapabilityId.of("sample.capability"));

        assertEquals(CapabilityLifecycleState.DEACTIVATED, engine.stateOf(CapabilityId.of("sample.capability")));
        assertTrue(serviceRegistry.lookup(SampleService.class).isEmpty());
        assertTrue(platformCapabilityRegistry.findById("sample.capability").isEmpty());
        assertTrue(engine.activeCommands().isEmpty());
        assertThrows(
                IllegalStateException.class,
                () -> documentEngine.createDocument(new CreateDocumentCommand("sample.document", "blocked")));
        assertTrue(documentEngine.findById(created.id()).isPresent());
    }

    @Test
    void eventSubscriptionRemovedAfterDeactivation() {
        AtomicBoolean handlerInvoked = new AtomicBoolean();
        Capability subscribing = new Capability() {
            private final CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
                    .id(CapabilityId.of("event.capability"))
                    .name("Event capability")
                    .version(CapabilityVersion.of("1.0.0"))
                    .description("Event subscription test")
                    .commands(List.of(CommandDescriptor.of("event.capability.cmd", "Cmd", List.of())))
                    .build();

            @Override
            public CapabilityDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public void onInitialize() {
            }

            @Override
            public void onActivate() {
                CapabilityRuntimeAccess.eventBus(stubPlatformCore())
                        .subscribeDomain(TestDomainEvent.class, event -> handlerInvoked.set(true));
            }

            @Override
            public void onDeactivate() {
            }

            @Override
            public void onStop() {
            }

            private PlatformCore stubPlatformCore() {
                return new StubPlatformCore(platformCapabilityRegistry, serviceRegistry, eventBus);
            }
        };

        CapabilityRegistry registry = new CapabilityRegistry();
        CapabilityContributionCatalogs localCatalogs = new CapabilityContributionCatalogs();
        CapabilityExternalContributionRegistry localExternal = new CapabilityExternalContributionRegistry();
        CapabilityEventSubscriptionRegistry localEvents = new CapabilityEventSubscriptionRegistry();
        PlatformCore platformCore = new StubPlatformCore(platformCapabilityRegistry, serviceRegistry, eventBus);
        CapabilityRegistrationService registrationService = new CapabilityRegistrationService(
                registry, localCatalogs, localExternal, localEvents, platformCore, documentEngine);
        CapabilityLifecycleManager lifecycleManager = new CapabilityLifecycleManager(
                registry, localCatalogs, localExternal, localEvents, platformCore);
        DefaultCapabilityEngine localEngine = new DefaultCapabilityEngine(
                new CapabilityDiscovery(List.of(subscribing)),
                registrationService,
                lifecycleManager,
                registry,
                localCatalogs);
        localEngine.discoverAndRegisterAll();
        localEngine.activateAll();

        eventBus.publish(new TestDomainEvent());
        assertTrue(handlerInvoked.get());

        handlerInvoked.set(false);
        localEngine.deactivate(CapabilityId.of("event.capability"));
        eventBus.publish(new TestDomainEvent());
        assertTrue(!handlerInvoked.get());
    }

    private Capability testCapability() {
        SampleServiceImpl service = new SampleServiceImpl();
        CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
                .id(CapabilityId.of("sample.capability"))
                .name("Sample capability")
                .version(CapabilityVersion.of("1.0.0"))
                .description("Deactivation acceptance capability")
                .permissions(List.of(PermissionDescriptor.of("sample.capability.perm", "Perm", "desc")))
                .commands(List.of(CommandDescriptor.of("sample.capability.cmd", "Cmd", List.of())))
                .documents(List.of(DocumentContribution.of(
                        "sample.document", "Sample document", "desc", processorFor("sample.document"))))
                .publicServices(List.of(PublicServiceContribution.of(SampleService.class, service)))
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
            }

            @Override
            public void onActivate() {
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

    private interface SampleService {
    }

    private static final class SampleServiceImpl implements SampleService {
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
            return "event.capability";
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

    private static final class RecordingDocumentEngine implements DocumentEngine {
        private final List<DocumentTypeDescriptor> types = new ArrayList<>();
        private final List<DocumentMetadata> documents = new ArrayList<>();

        @Override
        public DocumentProcessorRegistration registerProcessor(DocumentProcessor processor) {
            types.add(new DocumentTypeDescriptor(
                    processor.documentTypeId(), processor.documentTypeId(), "Registered"));
            return new DocumentProcessorRegistration() {
                @Override
                public String documentTypeId() {
                    return processor.documentTypeId();
                }

                @Override
                public void unregister() {
                    types.removeIf(type -> type.typeId().equals(processor.documentTypeId()));
                }

                @Override
                public void deactivate() {
                    types.removeIf(type -> type.typeId().equals(processor.documentTypeId()));
                }
            };
        }

        @Override
        public DocumentMetadata createDocument(CreateDocumentCommand command) {
            if (types.stream().noneMatch(type -> type.typeId().equals(command.documentTypeId()))) {
                throw new IllegalStateException("Document processor deactivated for type: " + command.documentTypeId());
            }
            DocumentMetadata metadata = new DocumentMetadata(
                    UUID.randomUUID(),
                    command.documentTypeId(),
                    "DOC-1",
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
            return documents.stream().filter(doc -> doc.id().equals(documentId)).findFirst();
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
}
