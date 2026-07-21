package com.tmp.document;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.LifecycleEventType;
import com.tmp.document.api.port.LifecycleJournalPort;
import com.tmp.document.support.TestDocumentProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = DefaultDocumentEngineTest.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tmp_document_engine;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class DefaultDocumentEngineTest {

    private static final String TYPE_ID = "platform.test";

    @Autowired
    private DocumentEngine documentEngine;

    @Autowired
    private LifecycleJournalPort lifecycleJournalPort;

    @BeforeEach
    void registerProcessor() {
        try {
            documentEngine.registerProcessor(new TestDocumentProcessor(TYPE_ID));
        } catch (IllegalStateException alreadyRegistered) {
            // Processor remains registered for the life of the shared Spring context.
        }
    }

    @Test
    void createsPostsUnpostsAndDeletesDocument() {
        DocumentMetadata created = documentEngine.createDocument(
                new CreateDocumentCommand(TYPE_ID, "Test document"));
        assertEquals(DocumentStatus.DRAFT, created.status());

        DocumentMetadata posted = documentEngine.postDocument(created.id());
        assertEquals(DocumentStatus.POSTED, posted.status());

        DocumentMetadata unposted = documentEngine.unpostDocument(created.id());
        assertEquals(DocumentStatus.DRAFT, unposted.status());

        documentEngine.deleteDocument(created.id());
        assertTrue(documentEngine.findById(created.id()).isEmpty());
    }

    @Test
    void recordsLifecycleJournalEntries() {
        DocumentMetadata created = documentEngine.createDocument(
                new CreateDocumentCommand(TYPE_ID, "Journal document"));
        documentEngine.postDocument(created.id());

        assertFalse(lifecycleJournalPort.findByDocumentId(created.id()).isEmpty());
        assertTrue(lifecycleJournalPort.findByDocumentId(created.id()).stream()
                .anyMatch(entry -> entry.eventType() == LifecycleEventType.POSTED));
    }

    @Test
    void searchFindsDocumentsByType() {
        documentEngine.createDocument(new CreateDocumentCommand(TYPE_ID, "Searchable"));
        assertFalse(documentEngine.search(new DocumentQuery(
                java.util.Optional.of(TYPE_ID),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                10,
                0)).isEmpty());
    }

    @SpringBootApplication
    @Import({
            com.tmp.infra.db.DatabaseAutoConfiguration.class,
            com.tmp.core.PlatformCoreAutoConfiguration.class,
            DocumentEngineAutoConfiguration.class
    })
    static class TestApplication {
    }
}
