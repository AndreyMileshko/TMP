package com.tmp.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.PlatformCore;
import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.LifecycleEventType;
import com.tmp.document.api.event.DocumentPostedEvent;
import com.tmp.document.api.port.LifecycleJournalPort;
import com.tmp.document.support.JdbcSideEffectDocumentProcessor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = DocumentEngineMultiDocumentTransactionIT.TestApplication.class)
@ActiveProfiles("test")
class DocumentEngineMultiDocumentTransactionIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private DocumentEngine documentEngine;

    @Autowired
    private PlatformCore platformCore;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LifecycleJournalPort lifecycleJournalPort;

    @BeforeEach
    void createSideEffectTable() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS documents.multi_document_tx_side_effects (
                    document_id UUID PRIMARY KEY,
                    owner_key VARCHAR(64) NOT NULL,
                    value VARCHAR(64) NOT NULL
                )
                """);
    }

    @Test
    void publicLifecycleContractUsesRequiredAndNeverRequiresNew() {
        Transactional typeLevel = DefaultDocumentEngine.class.getAnnotation(Transactional.class);
        assertNotNull(typeLevel);
        assertEquals(Propagation.REQUIRED, typeLevel.propagation());

        for (Method method : DefaultDocumentEngine.class.getDeclaredMethods()) {
            Transactional methodTx = method.getAnnotation(Transactional.class);
            if (methodTx != null) {
                assertNotEquals(
                        Propagation.REQUIRES_NEW,
                        methodTx.propagation(),
                        method.getName());
            }
        }
        assertTrue(transactionManager instanceof DataSourceTransactionManager);
        assertSame(
                jdbcTemplate.getDataSource(),
                ((DataSourceTransactionManager) transactionManager).getDataSource());
    }

    @Test
    void successCommitsBothDocumentsJournalsAndSideEffects() {
        String typeA = uniqueType("ok.a");
        String typeB = uniqueType("ok.b");
        JdbcSideEffectDocumentProcessor processorA = registerOwner(typeA, "cap-a");
        JdbcSideEffectDocumentProcessor processorB = registerOwner(typeB, "cap-b");
        AtomicBoolean ambientStillActiveInProcessorTx = new AtomicBoolean();

        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        RecordedPair committed = outer.execute(status -> {
            DocumentMetadata createdA = documentEngine.createDocument(
                    new CreateDocumentCommand(typeA, "document-a"));
            DocumentMetadata postedA = documentEngine.postDocument(createdA.id());
            DocumentMetadata createdB = documentEngine.createDocument(
                    new CreateDocumentCommand(typeB, "document-b"));
            DocumentMetadata postedB = documentEngine.postDocument(createdB.id());
            TransactionTemplate nested = new TransactionTemplate(transactionManager);
            nested.executeWithoutResult(inner ->
                    ambientStillActiveInProcessorTx.set(
                            processorA.observedActiveTransaction()
                                    && processorB.observedActiveTransaction()
                                    && TransactionSynchronizationManager.isActualTransactionActive()
                                    && !inner.isNewTransaction()
                                    && status.isNewTransaction()));
            return new RecordedPair(postedA, postedB);
        });

        assertTrue(ambientStillActiveInProcessorTx.get());
        assertEquals(DocumentStatus.POSTED, committed.documentA().status());
        assertEquals(DocumentStatus.POSTED, committed.documentB().status());
        assertEquals(
                DocumentStatus.POSTED,
                documentEngine.findById(committed.documentA().id()).orElseThrow().status());
        assertEquals(
                DocumentStatus.POSTED,
                documentEngine.findById(committed.documentB().id()).orElseThrow().status());
        assertTrue(hasJournalEvent(committed.documentA().id(), LifecycleEventType.POSTED));
        assertTrue(hasJournalEvent(committed.documentB().id(), LifecycleEventType.POSTED));
        assertEquals(1, countSideEffects("cap-a"));
        assertEquals(1, countSideEffects("cap-b"));
        assertTrue(processorA.observedActiveTransaction());
        assertTrue(processorB.observedActiveTransaction());
    }

    @Test
    void secondDocumentFailureRollsBackFirstDocumentAndAllSideEffects() {
        String typeA = uniqueType("fail.a");
        String typeB = uniqueType("fail.b");
        JdbcSideEffectDocumentProcessor processorA = registerOwner(typeA, "cap-a-fail");
        JdbcSideEffectDocumentProcessor processorB = registerOwner(typeB, "cap-b-fail");
        processorB.failOnPost();

        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        UUID[] ids = new UUID[2];
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                outer.executeWithoutResult(status -> {
                    DocumentMetadata createdA = documentEngine.createDocument(
                            new CreateDocumentCommand(typeA, "document-a"));
                    DocumentMetadata postedA = documentEngine.postDocument(createdA.id());
                    ids[0] = postedA.id();
                    assertEquals(DocumentStatus.POSTED, postedA.status());
                    assertEquals(1, countSideEffects("cap-a-fail"));
                    DocumentMetadata createdB = documentEngine.createDocument(
                            new CreateDocumentCommand(typeB, "document-b"));
                    ids[1] = createdB.id();
                    documentEngine.postDocument(createdB.id());
                }));

        assertTrue(thrown.getMessage().contains("Simulated capability failure"));
        assertTrue(documentEngine.findById(ids[0]).isEmpty());
        assertTrue(documentEngine.findById(ids[1]).isEmpty());
        assertEquals(0, countJournal(ids[0]));
        assertEquals(0, countJournal(ids[1]));
        assertEquals(0, countSideEffects("cap-a-fail"));
        assertEquals(0, countSideEffects("cap-b-fail"));
        assertTrue(processorA.observedActiveTransaction());
        assertTrue(processorB.observedActiveTransaction());
    }

    @Test
    void afterCommitEventsPublishOnlyAfterOuterCommit() {
        String typeA = uniqueType("events.a");
        String typeB = uniqueType("events.b");
        registerOwner(typeA, "cap-a-events");
        registerOwner(typeB, "cap-b-events");
        List<DocumentPostedEvent> events = new ArrayList<>();
        var subscription = platformCore.eventBus().subscribeDomain(
                DocumentPostedEvent.class,
                event -> events.add((DocumentPostedEvent) event));
        try {
            AtomicInteger eventsInsideOuterTx = new AtomicInteger();
            TransactionTemplate outer = new TransactionTemplate(transactionManager);
            RecordedPair committed = outer.execute(status -> {
                DocumentMetadata postedA = documentEngine.postDocument(
                        documentEngine.createDocument(
                                new CreateDocumentCommand(typeA, "document-a")).id());
                DocumentMetadata postedB = documentEngine.postDocument(
                        documentEngine.createDocument(
                                new CreateDocumentCommand(typeB, "document-b")).id());
                eventsInsideOuterTx.set(events.size());
                return new RecordedPair(postedA, postedB);
            });

            assertEquals(0, eventsInsideOuterTx.get());
            assertEquals(2, events.size());
            assertTrue(events.stream().anyMatch(event ->
                    event.documentId().equals(committed.documentA().id())));
            assertTrue(events.stream().anyMatch(event ->
                    event.documentId().equals(committed.documentB().id())));
        } finally {
            subscription.unsubscribe();
        }
    }

    @Test
    void afterCommitEventsAreNotPublishedWhenSecondDocumentRollsBack() {
        String typeA = uniqueType("events.rollback.a");
        String typeB = uniqueType("events.rollback.b");
        registerOwner(typeA, "cap-a-events-rb");
        JdbcSideEffectDocumentProcessor processorB = registerOwner(typeB, "cap-b-events-rb");
        processorB.failOnPost();
        List<DocumentPostedEvent> events = new ArrayList<>();
        var subscription = platformCore.eventBus().subscribeDomain(
                DocumentPostedEvent.class,
                event -> events.add((DocumentPostedEvent) event));
        try {
            TransactionTemplate outer = new TransactionTemplate(transactionManager);
            assertThrows(IllegalStateException.class, () ->
                    outer.executeWithoutResult(status -> {
                        documentEngine.postDocument(
                                documentEngine.createDocument(
                                        new CreateDocumentCommand(typeA, "document-a")).id());
                        documentEngine.postDocument(
                                documentEngine.createDocument(
                                        new CreateDocumentCommand(typeB, "document-b")).id());
                    }));
            assertTrue(events.isEmpty());
            assertEquals(0, countSideEffects("cap-a-events-rb"));
            assertEquals(0, countSideEffects("cap-b-events-rb"));
        } finally {
            subscription.unsubscribe();
        }
    }

    private JdbcSideEffectDocumentProcessor registerOwner(String typeId, String ownerKey) {
        JdbcSideEffectDocumentProcessor processor =
                new JdbcSideEffectDocumentProcessor(typeId, ownerKey, jdbcTemplate);
        documentEngine.registerProcessor(processor);
        return processor;
    }

    private boolean hasJournalEvent(UUID documentId, LifecycleEventType eventType) {
        return lifecycleJournalPort.findByDocumentId(documentId).stream()
                .anyMatch(entry -> entry.eventType() == eventType);
    }

    private int countJournal(UUID documentId) {
        return lifecycleJournalPort.findByDocumentId(documentId).size();
    }

    private int countSideEffects(String ownerKey) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents.multi_document_tx_side_effects WHERE owner_key = ?",
                Integer.class,
                ownerKey);
        return count == null ? 0 : count;
    }

    private static String uniqueType(String suffix) {
        return "platform.multi.tx." + suffix + "." + UUID.randomUUID();
    }

    private record RecordedPair(DocumentMetadata documentA, DocumentMetadata documentB) {
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
