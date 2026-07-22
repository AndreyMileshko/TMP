package com.tmp.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.event.EventSubscription;
import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.LifecycleEventType;
import com.tmp.document.api.event.DocumentCreatedEvent;
import com.tmp.document.api.port.LifecycleJournalPort;
import com.tmp.document.support.ConfigurableDocumentProcessor;
import com.tmp.document.support.TestDocumentProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

    @Autowired
    private LifecycleJournalPort lifecycleJournalPort;

    private final List<DocumentCreatedEvent> receivedEvents = new ArrayList<>();
    private EventSubscription collectorSubscription;

    @BeforeEach
    void setUp() {
        receivedEvents.clear();
        collectorSubscription = platformCore.eventBus().subscribeDomain(DocumentCreatedEvent.class, event ->
                receivedEvents.add((DocumentCreatedEvent) event));
        try {
            documentEngine.registerProcessor(new TestDocumentProcessor(TYPE_ID));
        } catch (IllegalStateException alreadyRegistered) {
            // shared context
        }
    }

    @AfterEach
    void tearDown() {
        if (collectorSubscription != null) {
            collectorSubscription.unsubscribe();
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

    @Test
    void failingAfterCommitHandlerDoesNotFailDocumentOperation() {
        String typeId = uniqueType("handler.fail");
        documentEngine.registerProcessor(new TestDocumentProcessor(typeId));
        EventSubscription failingSubscription = platformCore.eventBus().subscribeDomain(
                DocumentCreatedEvent.class,
                event -> {
                    throw new IllegalStateException("subscriber failed after commit");
                });
        try {
            DocumentMetadata created = documentEngine.createDocument(
                    new CreateDocumentCommand(typeId, "Committed despite handler failure"));

            assertEquals(typeId, created.documentTypeId());
            assertTrue(documentEngine.findById(created.id()).isPresent());
            assertEquals(
                    1,
                    lifecycleJournalPort.findByDocumentId(created.id()).stream()
                            .filter(entry -> entry.eventType() == LifecycleEventType.CREATED)
                            .count());
        } finally {
            failingSubscription.unsubscribe();
        }
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
