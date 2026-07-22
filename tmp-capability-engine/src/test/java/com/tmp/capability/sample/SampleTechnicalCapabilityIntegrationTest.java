package com.tmp.capability.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.CapabilityEngineAutoConfiguration;
import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.core.PlatformCoreAutoConfiguration;
import com.tmp.document.DocumentEngineAutoConfiguration;
import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = SampleTechnicalCapabilityIntegrationTest.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:tmp_capability_sample;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password="
        })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SampleTechnicalCapabilityIntegrationTest {

    @Autowired
    private CapabilityEngine capabilityEngine;

    @Autowired
    private DocumentEngine documentEngine;

    @Test
    @Order(1)
    void bothSampleCapabilitiesDiscoverRegisterInitializeAndActivate() {
        assertEquals(CapabilityLifecycleState.ACTIVE, capabilityEngine.stateOf(SampleTechnicalCapability.ID));
        assertEquals(CapabilityLifecycleState.ACTIVE, capabilityEngine.stateOf(SampleDependentTechnicalCapability.ID));
        assertEquals(2, capabilityEngine.status().activeCount());
    }

    @Test
    @Order(2)
    void dependentActivatesStrictlyAfterItsDependency() {
        assertEquals(
                java.util.List.of(
                        SampleTechnicalCapability.ID.value(), SampleDependentTechnicalCapability.ID.value()),
                SampleLifecycleProbe.activationOrder());
    }

    @Test
    @Order(3)
    void dependentResolvesPublicServiceThroughPlatformCoreServiceRegistry() {
        SampleTechnicalService resolved = SampleLifecycleProbe.resolvedService();
        assertNotNull(resolved);
        assertEquals("sample-technical-service", resolved.marker());
    }

    @Test
    @Order(4)
    void documentTypeIsRegisteredAndCreatableThroughDocumentEnginePublicApi() {
        assertTrue(documentEngine.registeredTypes().stream()
                .anyMatch(type -> SampleTechnicalDocumentProcessor.DOCUMENT_TYPE_ID.equals(type.typeId())));

        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(
                SampleTechnicalDocumentProcessor.DOCUMENT_TYPE_ID, "Sample technical document instance"));
        assertNotNull(created.id());
        assertEquals(SampleTechnicalDocumentProcessor.DOCUMENT_TYPE_ID, created.documentTypeId());
    }

    @Test
    @Order(5)
    void deactivationScenariosRespectActiveDependents() {
        assertThrows(
                IllegalStateException.class, () -> capabilityEngine.deactivate(SampleTechnicalCapability.ID));

        capabilityEngine.deactivate(SampleDependentTechnicalCapability.ID);
        assertEquals(
                CapabilityLifecycleState.DEACTIVATED,
                capabilityEngine.stateOf(SampleDependentTechnicalCapability.ID));

        capabilityEngine.deactivate(SampleTechnicalCapability.ID);
        assertEquals(CapabilityLifecycleState.DEACTIVATED, capabilityEngine.stateOf(SampleTechnicalCapability.ID));
    }

    @SpringBootApplication
    @Import({
        com.tmp.infra.db.DatabaseAutoConfiguration.class,
        PlatformCoreAutoConfiguration.class,
        DocumentEngineAutoConfiguration.class,
        CapabilityEngineAutoConfiguration.class,
        SampleTechnicalCapabilityIntegrationTest.SampleBeans.class
    })
    static class TestApplication {
    }

    @Configuration
    static class SampleBeans {

        @Bean
        SampleTechnicalCapability sampleTechnicalCapability() {
            return new SampleTechnicalCapability();
        }

        @Bean
        SampleDependentTechnicalCapability sampleDependentTechnicalCapability(
                com.tmp.core.api.PlatformCore platformCore) {
            return new SampleDependentTechnicalCapability(platformCore);
        }
    }
}
