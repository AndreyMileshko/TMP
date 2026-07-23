package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.DocumentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = TmpBootstrapApplication.class)
@ActiveProfiles("test")
class DocumentEngineIntegrationIT {

    private static final String TYPE_ID = "platform.integration";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("tmp.security.bootstrap.admin-login", () -> "admin");
        registry.add("tmp.security.bootstrap.admin-display-name", () -> "Administrator");
        registry.add("tmp.security.bootstrap.admin-password", () -> "test-admin-password");
    }

    @Autowired
    private DocumentEngine documentEngine;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void registerProcessorIfNeeded() {
        try {
            documentEngine.registerProcessor(new TestDocumentProcessor(TYPE_ID));
        } catch (IllegalStateException alreadyRegistered) {
            // Processor remains registered for the life of the shared Spring context.
        }
    }

    @Test
    void flywayCreatesDocumentsSchema() {
        Integer schemaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'documents'",
                Integer.class);
        assertEquals(1, schemaCount);
    }

    @Test
    void documentLifecycleWorksThroughBootstrapContext() {
        DocumentMetadata created = documentEngine.createDocument(
                new CreateDocumentCommand(TYPE_ID, "Bootstrap integration document"));
        DocumentMetadata posted = documentEngine.postDocument(created.id());
        assertEquals(DocumentStatus.POSTED, posted.status());
        assertFalse(documentEngine.search(com.tmp.document.api.DocumentQuery.all(10)).isEmpty());
    }

    @Test
    void desktopBootstrapFormatsDocumentsForShell() {
        documentEngine.createDocument(new CreateDocumentCommand(TYPE_ID, "Shell visible"));
        String panel = DesktopBootstrap.formatDocumentPanel(documentEngine);
        assertTrue(panel.contains("Document Engine"));
        assertTrue(panel.contains("Shell visible"));
    }

    private static final class TestDocumentProcessor implements DocumentProcessor {

        private final String typeId;

        TestDocumentProcessor(String typeId) {
            this.typeId = typeId;
        }

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
    }
}
