package com.tmp.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.core.PlatformCoreAutoConfiguration;
import com.tmp.core.api.PlatformCore;
import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentEngineStatus;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentTypeDescriptor;
import com.tmp.document.api.UpdateDocumentCommand;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = CapabilityEngineAutoConfigurationTest.TestApplication.class)
@SpringBootTest
class CapabilityEngineAutoConfigurationTest {

    private static final CapabilityId TEST_CAPABILITY_ID = CapabilityId.of("test.auto.config.capability");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private PlatformCore platformCore;

    @Autowired
    private CapabilityEngine capabilityEngine;

    @Autowired
    private CapabilityEnginePlatformComponent capabilityEnginePlatformComponent;

    @Test
    void exactlyOneCapabilityEngineBeanPresent() {
        Map<String, CapabilityEngine> beans = applicationContext.getBeansOfType(CapabilityEngine.class);
        assertEquals(1, beans.size());
        assertNotNull(capabilityEngine);
    }

    @Test
    void componentRegisteredInPlatformCoreAfterStartup() {
        assertTrue(platformCore.platformRegistry().findById("capability-engine").isPresent());
    }

    @Test
    void adapterLifecycleDelegatesToCapabilityEngine() {
        TrackingCapability tracking = applicationContext.getBean(TrackingCapability.class);

        // PlatformCoreLifecycleListener triggers initialize+start via ApplicationReadyEvent
        assertEquals(CapabilityLifecycleState.ACTIVE, capabilityEngine.stateOf(TEST_CAPABILITY_ID));
        assertEquals(1, tracking.initializeCount.get());
        assertEquals(1, tracking.activateCount.get());

        capabilityEnginePlatformComponent.stop();
        assertEquals(1, tracking.stopCount.get());
        assertEquals(CapabilityLifecycleState.STOPPED, capabilityEngine.stateOf(TEST_CAPABILITY_ID));
    }

    @Import({PlatformCoreAutoConfiguration.class, CapabilityEngineAutoConfiguration.class, TestConfig.class})
    @Configuration
    static class TestApplication {
    }

    @Configuration
    static class TestConfig {

        @Bean
        TrackingCapability testCapability() {
            return new TrackingCapability();
        }

        @Bean
        DocumentEngine documentEngine() {
            return new EmptyDocumentEngine();
        }
    }

    static final class TrackingCapability implements Capability {

        private final AtomicInteger initializeCount = new AtomicInteger();
        private final AtomicInteger activateCount = new AtomicInteger();
        private final AtomicInteger stopCount = new AtomicInteger();

        private final CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
                .id(TEST_CAPABILITY_ID)
                .name("Auto-config test capability")
                .version(CapabilityVersion.of("1.0.0"))
                .description("Tracks lifecycle delegation from CapabilityEnginePlatformComponent")
                .build();

        @Override
        public CapabilityDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public void onInitialize() {
            initializeCount.incrementAndGet();
        }

        @Override
        public void onActivate() {
            activateCount.incrementAndGet();
        }

        @Override
        public void onDeactivate() {
            // test double: no-op
        }

        @Override
        public void onStop() {
            stopCount.incrementAndGet();
        }
    }

    private static final class EmptyDocumentEngine implements DocumentEngine {
        @Override
        public DocumentProcessorRegistration registerProcessor(DocumentProcessor processor) {
            String typeId = processor.documentTypeId();
            return new DocumentProcessorRegistration() {
                @Override
                public String documentTypeId() {
                    return typeId;
                }

                @Override
                public void unregister() {
                    // no documents in auto-config slice test
                }

                @Override
                public void deactivate() {
                    // no documents in auto-config slice test
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
            return List.of();
        }

        @Override
        public DocumentEngineStatus status() {
            throw new UnsupportedOperationException();
        }
    }
}
