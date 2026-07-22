package com.tmp.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.PlatformCore;
import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.LifecycleEventType;
import com.tmp.document.api.UpdateDocumentCommand;
import com.tmp.document.api.event.DocumentCreatedEvent;
import com.tmp.document.api.port.DocumentFileReference;
import com.tmp.document.api.port.DocumentFileStoragePort;
import com.tmp.document.api.port.DocumentVersionPort;
import com.tmp.document.api.port.LifecycleJournalPort;
import com.tmp.document.support.ConfigurableDocumentProcessor;
import com.tmp.document.support.TestDocumentProcessor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = DocumentEnginePostgresIntegrationIT.TestApplication.class)
@ActiveProfiles("test")
class DocumentEnginePostgresIntegrationIT {

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
    private DocumentEngine documentEngine;

    @Autowired
    private DefaultDocumentProcessorRegistry processorRegistry;

    @Autowired
    private PlatformCore platformCore;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LifecycleJournalPort lifecycleJournalPort;

    @Autowired
    private DocumentVersionPort documentVersionPort;

    @Autowired
    private DocumentFileStoragePort documentFileStoragePort;

    @Test
    void processorRegistrationRollbackClearsDbAndRegistry() {
        String typeId = uniqueType("reg.rollback");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            documentEngine.registerProcessor(new TestDocumentProcessor(typeId));
            status.setRollbackOnly();
        });

        assertEquals(0, countTypes(typeId));
        assertFalse(processorRegistry.isRegistered(typeId));

        documentEngine.registerProcessor(new TestDocumentProcessor(typeId));
        assertTrue(processorRegistry.isRegistered(typeId));
        assertEquals(1, countTypes(typeId));
    }

    @Test
    void processorOperationRollbackKeepsDocumentDraft() {
        String typeId = uniqueType("op.rollback");
        ConfigurableDocumentProcessor processor = new ConfigurableDocumentProcessor(typeId);
        documentEngine.registerProcessor(processor);
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(typeId, "post fail"));
        processor.failOn(ConfigurableDocumentProcessor.FailurePoint.ON_POST);

        assertThrows(IllegalStateException.class, () -> documentEngine.postDocument(created.id()));
        assertEquals(DocumentStatus.DRAFT, documentEngine.findById(created.id()).orElseThrow().status());
    }

    @Test
    void optimisticLockingConflictIsRejected() {
        String typeId = uniqueType("opt.lock");
        documentEngine.registerProcessor(new TestDocumentProcessor(typeId));
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(typeId, "v0"));

        documentEngine.updateDocument(new UpdateDocumentCommand(created.id(), "v1", created.version()));
        assertThrows(
                IllegalStateException.class,
                () -> documentEngine.updateDocument(
                        new UpdateDocumentCommand(created.id(), "stale", created.version())));
    }

    @Test
    void concurrentPostProducesSingleSuccess() throws Exception {
        String typeId = uniqueType("concurrent.post");
        documentEngine.registerProcessor(new TestDocumentProcessor(typeId));
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(typeId, "post"));
        assertEquals(1, runConcurrent(4, () -> documentEngine.postDocument(created.id())));
        assertEquals(DocumentStatus.POSTED, documentEngine.findById(created.id()).orElseThrow().status());
    }

    @Test
    void concurrentUpdateProducesSingleSuccess() throws Exception {
        String typeId = uniqueType("concurrent.update");
        documentEngine.registerProcessor(new TestDocumentProcessor(typeId));
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(typeId, "upd"));
        assertEquals(1, runConcurrent(4, () -> documentEngine.updateDocument(
                new UpdateDocumentCommand(created.id(), "Title", created.version()))));
        assertEquals(created.version() + 1, documentEngine.findById(created.id()).orElseThrow().version());
    }

    @Test
    void eventEmittedAfterCommitAndNotAfterRollback() {
        String typeId = uniqueType("events");
        documentEngine.registerProcessor(new TestDocumentProcessor(typeId));
        List<DocumentCreatedEvent> events = new ArrayList<>();
        var subscription = platformCore.eventBus().subscribeDomain(DocumentCreatedEvent.class, event ->
                events.add((DocumentCreatedEvent) event));
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            DocumentMetadata committed = tx.execute(status ->
                    documentEngine.createDocument(new CreateDocumentCommand(typeId, "committed")));
            assertEquals(1, events.size());
            assertEquals(committed.id(), events.getFirst().documentId());

            events.clear();
            UUID rolledBackId = tx.execute(status -> {
                DocumentMetadata created = documentEngine.createDocument(
                        new CreateDocumentCommand(typeId, "rolled-back"));
                status.setRollbackOnly();
                return created.id();
            });
            assertTrue(events.isEmpty());
            assertTrue(documentEngine.findById(rolledBackId).isEmpty());
        } finally {
            subscription.unsubscribe();
        }
    }

    @Test
    void failingEventSubscriberDoesNotUndoCommittedDocument() {
        String typeId = uniqueType("fail.sub");
        documentEngine.registerProcessor(new TestDocumentProcessor(typeId));
        var subscription = platformCore.eventBus().subscribeDomain(DocumentCreatedEvent.class, event -> {
            throw new IllegalStateException("postgres subscriber failed");
        });
        try {
            DocumentMetadata created = documentEngine.createDocument(
                    new CreateDocumentCommand(typeId, "survives handler failure"));
            assertTrue(documentEngine.findById(created.id()).isPresent());
            assertEquals(
                    1,
                    lifecycleJournalPort.findByDocumentId(created.id()).stream()
                            .filter(entry -> entry.eventType() == LifecycleEventType.CREATED)
                            .count());
        } finally {
            subscription.unsubscribe();
        }
    }

    @Test
    void versionSnapshotsAndLifecycleJournalPersist() {
        String typeId = uniqueType("snapshots");
        documentEngine.registerProcessor(new TestDocumentProcessor(typeId));
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(typeId, "snap-0"));
        documentEngine.updateDocument(new UpdateDocumentCommand(created.id(), "snap-1", created.version()));

        assertFalse(documentVersionPort.findByDocumentId(created.id()).isEmpty());
        assertTrue(lifecycleJournalPort.findByDocumentId(created.id()).stream()
                .anyMatch(entry -> entry.eventType() == LifecycleEventType.UPDATED));
    }

    @Test
    void documentFileStorageWorksOnPostgres() {
        String typeId = uniqueType("files");
        documentEngine.registerProcessor(new TestDocumentProcessor(typeId));
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(typeId, "with-file"));
        documentFileStoragePort.registerReference(new DocumentFileReference(
                UUID.randomUUID(),
                created.id(),
                "/pg/storage/file.bin",
                "application/octet-stream",
                8L,
                "checksum",
                Instant.now()));

        assertEquals(1, documentFileStoragePort.findByDocumentId(created.id()).size());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents.document_files WHERE document_id = ?",
                Integer.class,
                created.id()).intValue());
    }

    @Test
    void foreignKeyDocumentTypeConstraintExists() {
        Integer fkCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE table_schema = 'documents'
                  AND table_name = 'documents'
                  AND constraint_name = 'fk_documents_document_type'
                  AND constraint_type = 'FOREIGN KEY'
                """,
                Integer.class);
        assertEquals(1, fkCount);
    }

    private int countTypes(String typeId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents.document_types WHERE id = ?",
                Integer.class,
                typeId);
        return count == null ? 0 : count;
    }

    private static int runConcurrent(int threads, Runnable action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                try {
                    action.run();
                    success.incrementAndGet();
                } catch (RuntimeException ignored) {
                    // conflicts expected
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(15, TimeUnit.SECONDS);
        }
        executor.shutdown();
        return success.get();
    }

    private static String uniqueType(String suffix) {
        return "platform.pg." + suffix + "." + UUID.randomUUID();
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
