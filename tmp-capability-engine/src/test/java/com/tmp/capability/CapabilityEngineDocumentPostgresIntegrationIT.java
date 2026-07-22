package com.tmp.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.DocumentContribution;
import com.tmp.capability.registration.CapabilityRegistrationException;
import com.tmp.capability.registration.CapabilityRegistrationService;
import com.tmp.capability.sample.SampleDependentTechnicalCapability;
import com.tmp.capability.sample.SampleTechnicalCapability;
import com.tmp.capability.sample.SampleTechnicalDocumentProcessor;
import com.tmp.core.PlatformCoreAutoConfiguration;
import com.tmp.document.DocumentEngineAutoConfiguration;
import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentStatus;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = CapabilityEngineDocumentPostgresIntegrationIT.TestApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CapabilityEngineDocumentPostgresIntegrationIT {

    private static final CapabilityId DUPLICATE_TEST_CAPABILITY_ID =
            CapabilityId.of("postgres.duplicate.test.capability");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private CapabilityEngine capabilityEngine;

    @Autowired
    private CapabilityRegistrationService registrationService;

    @Autowired
    private DocumentEngine documentEngine;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    void sampleCapabilityRegistersDocumentTypeInPostgres() {
        assertEquals(CapabilityLifecycleState.ACTIVE, capabilityEngine.stateOf(SampleTechnicalCapability.ID));
        assertEquals(1, countDocumentTypes(SampleTechnicalDocumentProcessor.DOCUMENT_TYPE_ID));
        assertTrue(documentEngine.registeredTypes().stream()
                .anyMatch(type -> SampleTechnicalDocumentProcessor.DOCUMENT_TYPE_ID.equals(type.typeId())));
    }

    @Test
    @Order(2)
    void documentLifecycleSucceedsAgainstRealPostgres() {
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(
                SampleTechnicalDocumentProcessor.DOCUMENT_TYPE_ID, "PostgreSQL capability document"));
        assertNotNull(created.id());
        assertEquals(DocumentStatus.DRAFT, created.status());

        DocumentMetadata posted = documentEngine.postDocument(created.id());
        assertEquals(DocumentStatus.POSTED, posted.status());

        assertTrue(documentEngine.findById(created.id()).isPresent());
        assertEquals(
                1,
                documentEngine.search(DocumentQuery.all(10)).stream()
                        .filter(doc -> doc.id().equals(created.id()))
                        .count());
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM documents.documents WHERE id = ?",
                        Integer.class,
                        created.id()));
    }

    @Test
    @Order(3)
    void duplicateDocumentTypeRegistrationLeavesNoCapabilityEngineRegistration() {
        int typesBefore = countDocumentTypes(SampleTechnicalDocumentProcessor.DOCUMENT_TYPE_ID);

        CapabilityRegistrationException failure = assertThrows(
                CapabilityRegistrationException.class,
                () -> registrationService.register(duplicateDocumentTypeCapability()));

        assertTrue(failure.getCause().getMessage().contains(SampleTechnicalDocumentProcessor.DOCUMENT_TYPE_ID));
        assertTrue(capabilityEngine.findById(DUPLICATE_TEST_CAPABILITY_ID).isEmpty());
        assertEquals(typesBefore, countDocumentTypes(SampleTechnicalDocumentProcessor.DOCUMENT_TYPE_ID));
    }

    @Test
    @Order(4)
    void deactivationBlocksNewDocumentOperationsButPreservesExistingDocuments() {
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(
                SampleTechnicalDocumentProcessor.DOCUMENT_TYPE_ID, "Document before deactivation"));
        assertEquals(1, countDocuments(created.id()));

        capabilityEngine.deactivate(SampleDependentTechnicalCapability.ID);
        capabilityEngine.deactivate(SampleTechnicalCapability.ID);

        assertEquals(CapabilityLifecycleState.DEACTIVATED, capabilityEngine.stateOf(SampleTechnicalCapability.ID));
        assertThrows(
                IllegalStateException.class,
                () -> documentEngine.createDocument(new CreateDocumentCommand(
                        SampleTechnicalDocumentProcessor.DOCUMENT_TYPE_ID, "blocked after deactivation")));
        assertTrue(documentEngine.findById(created.id()).isPresent());
        assertEquals(1, countDocuments(created.id()));
        assertEquals(1, countDocumentTypes(SampleTechnicalDocumentProcessor.DOCUMENT_TYPE_ID));
    }

    private int countDocuments(java.util.UUID documentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents.documents WHERE id = ?",
                Integer.class,
                documentId);
        return count == null ? 0 : count;
    }

    private int countDocumentTypes(String typeId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents.document_types WHERE id = ?",
                Integer.class,
                typeId);
        return count == null ? 0 : count;
    }

    private static Capability duplicateDocumentTypeCapability() {
        return new Capability() {
            private final CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
                    .id(DUPLICATE_TEST_CAPABILITY_ID)
                    .name("PostgreSQL duplicate registration test")
                    .version(CapabilityVersion.of("1.0.0"))
                    .description("Technical fixture for duplicate document type rollback proof")
                    .documents(List.of(DocumentContribution.of(
                            SampleTechnicalDocumentProcessor.DOCUMENT_TYPE_ID,
                            "Duplicate sample document",
                            "Technical fixture for duplicate-type rollback proof",
                            new SampleTechnicalDocumentProcessor())))
                    .build();

            @Override
            public CapabilityDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public void onInitialize() {
                // test fixture
            }

            @Override
            public void onActivate() {
                // test fixture
            }

            @Override
            public void onDeactivate() {
                // test fixture
            }

            @Override
            public void onStop() {
                // test fixture
            }
        };
    }

    @SpringBootApplication
    @Import({
        com.tmp.infra.db.DatabaseAutoConfiguration.class,
        PlatformCoreAutoConfiguration.class,
        DocumentEngineAutoConfiguration.class,
        CapabilityEngineAutoConfiguration.class,
        CapabilityEngineDocumentPostgresIntegrationIT.SampleBeans.class
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
