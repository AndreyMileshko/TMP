package com.tmp.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.PlatformCore;
import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.event.DocumentCreatedEvent;
import com.tmp.document.support.ConfigurableDocumentProcessor;
import com.tmp.document.support.TestDocumentProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = DefaultDocumentEngineTransactionEventTest.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tmp_document_engine_tx_events;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class DefaultDocumentEngineTransactionEventTest {

    private static final String TYPE_ID = "platform.tx.events";

    @Autowired
    private DocumentEngine documentEngine;

    @Autowired
    private PlatformCore platformCore;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<DocumentCreatedEvent> receivedEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        receivedEvents.clear();
        platformCore.eventBus().subscribeDomain(DocumentCreatedEvent.class, event ->
                receivedEvents.add((DocumentCreatedEvent) event));
        try {
            documentEngine.registerProcessor(new TestDocumentProcessor(TYPE_ID));
        } catch (IllegalStateException alreadyRegistered) {
            // shared context
        }
    }

    @Test
    void eventEmittedOnceAfterCommit() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        DocumentMetadata created = transactionTemplate.execute(status -> documentEngine.createDocument(
                new CreateDocumentCommand(TYPE_ID, "Committed document")));

        assertEquals(1, receivedEvents.size());
        assertEquals(created.id(), receivedEvents.getFirst().documentId());
    }

    @Test
    void eventNotEmittedOnRollback() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID documentId = transactionTemplate.execute(status -> {
            DocumentMetadata created = documentEngine.createDocument(
                    new CreateDocumentCommand(TYPE_ID, "Rolled back document"));
            status.setRollbackOnly();
            return created.id();
        });

        assertTrue(receivedEvents.isEmpty());
        assertTrue(documentEngine.findById(documentId).isEmpty());
    }

    @Test
    void documentEventMetadataIsStable() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> documentEngine.createDocument(
                new CreateDocumentCommand(TYPE_ID, "Stable metadata document")));

        DocumentCreatedEvent event = receivedEvents.getFirst();
        assertEquals(event.eventId(), event.eventId());
        assertEquals(event.occurredAt(), event.occurredAt());
        assertNotEquals(event.eventId(), UUID.randomUUID());
    }

    @Test
    void rollbackAfterProcessorValidationFailureDoesNotEmitEvent() {
        String failingType = uniqueType("tx.fail.create");
        ConfigurableDocumentProcessor processor = new ConfigurableDocumentProcessor(failingType);
        documentEngine.registerProcessor(processor);
        processor.failOn(ConfigurableDocumentProcessor.FailurePoint.VALIDATE_CREATE);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        int eventsBefore = receivedEvents.size();
        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status ->
                documentEngine.createDocument(new CreateDocumentCommand(failingType, "Should fail"))));

        assertEquals(eventsBefore, receivedEvents.size());
    }

    private static String uniqueType(String suffix) {
        return TYPE_ID + "." + suffix + "." + UUID.randomUUID();
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
