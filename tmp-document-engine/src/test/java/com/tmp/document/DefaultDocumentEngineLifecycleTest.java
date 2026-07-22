package com.tmp.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.LifecycleEventType;
import com.tmp.document.api.UpdateDocumentCommand;
import com.tmp.document.api.port.DocumentVersionPort;
import com.tmp.document.api.port.LifecycleJournalPort;
import com.tmp.document.support.ConfigurableDocumentProcessor;
import com.tmp.document.support.TestDocumentProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = DefaultDocumentEngineLifecycleTest.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tmp_document_engine_lifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class DefaultDocumentEngineLifecycleTest {

    private static final String TYPE_ID = "platform.lifecycle";

    @Autowired
    private DocumentEngine documentEngine;

    @Autowired
    private LifecycleJournalPort lifecycleJournalPort;

    @Autowired
    private DocumentVersionPort documentVersionPort;

    @BeforeEach
    void registerBaseProcessor() {
        try {
            documentEngine.registerProcessor(new TestDocumentProcessor(TYPE_ID));
        } catch (IllegalStateException alreadyRegistered) {
            // shared context
        }
    }

    @Test
    void createProcessorFailureRollsBackDocument() {
        String failType = uniqueType("create.fail");
        ConfigurableDocumentProcessor processor = registerConfigurable(failType);
        processor.failOn(ConfigurableDocumentProcessor.FailurePoint.VALIDATE_CREATE);

        assertThrows(
                IllegalStateException.class,
                () -> documentEngine.createDocument(new CreateDocumentCommand(failType, "Should not persist")));
        assertEquals(0L, documentEngine.search(com.tmp.document.api.DocumentQuery.all(100)).stream()
                .filter(document -> failType.equals(document.documentTypeId()))
                .count());
    }

    @Test
    void postProcessorFailureKeepsDocumentDraft() {
        String failType = uniqueType("post.fail");
        ConfigurableDocumentProcessor processor = registerConfigurable(failType);
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(failType, "Post fail"));
        processor.failOn(ConfigurableDocumentProcessor.FailurePoint.ON_POST);

        assertThrows(IllegalStateException.class, () -> documentEngine.postDocument(created.id()));
        assertEquals(DocumentStatus.DRAFT, documentEngine.findById(created.id()).orElseThrow().status());
    }

    @Test
    void unpostProcessorFailureKeepsDocumentPosted() {
        String failType = uniqueType("unpost.fail");
        ConfigurableDocumentProcessor processor = registerConfigurable(failType);
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(failType, "Unpost fail"));
        documentEngine.postDocument(created.id());
        processor.failOn(ConfigurableDocumentProcessor.FailurePoint.ON_UNPOST);

        assertThrows(IllegalStateException.class, () -> documentEngine.unpostDocument(created.id()));
        assertEquals(DocumentStatus.POSTED, documentEngine.findById(created.id()).orElseThrow().status());
    }

    @Test
    void invalidLifecycleTransitionsAreRejected() {
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(TYPE_ID, "Transitions"));
        DocumentMetadata draftForClose = documentEngine.createDocument(new CreateDocumentCommand(TYPE_ID, "Draft close"));
        DocumentMetadata posted = documentEngine.postDocument(created.id());

        assertThrows(IllegalStateException.class, () -> documentEngine.postDocument(created.id()));
        assertThrows(IllegalStateException.class, () -> documentEngine.closeDocument(draftForClose.id()));
        assertThrows(IllegalStateException.class, () -> documentEngine.unpostDocument(draftForClose.id()));

        DocumentMetadata closed = documentEngine.closeDocument(posted.id());
        assertEquals(DocumentStatus.CLOSED, closed.status());
        assertThrows(IllegalStateException.class, () -> documentEngine.postDocument(created.id()));
        assertThrows(IllegalStateException.class, () -> documentEngine.unpostDocument(created.id()));
    }

    @Test
    void postedAndClosedDocumentsAreImmutable() {
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(TYPE_ID, "Immutable"));
        DocumentMetadata posted = documentEngine.postDocument(created.id());
        assertThrows(
                IllegalStateException.class,
                () -> documentEngine.updateDocument(
                        new UpdateDocumentCommand(posted.id(), "Changed", posted.version())));

        DocumentMetadata closed = documentEngine.closeDocument(posted.id());
        assertThrows(
                IllegalStateException.class,
                () -> documentEngine.updateDocument(
                        new UpdateDocumentCommand(closed.id(), "Changed again", closed.version())));
    }

    @Test
    void deleteRestrictionsEnforceDraftOnly() {
        DocumentMetadata draft = documentEngine.createDocument(new CreateDocumentCommand(TYPE_ID, "Delete draft"));
        DocumentMetadata posted = documentEngine.createDocument(new CreateDocumentCommand(TYPE_ID, "Delete guard"));
        documentEngine.postDocument(posted.id());
        assertThrows(IllegalStateException.class, () -> documentEngine.deleteDocument(posted.id()));
        documentEngine.deleteDocument(draft.id());
        assertTrue(documentEngine.findById(draft.id()).isEmpty());
    }

    @Test
    void optimisticLockingConflictIsRejected() {
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(TYPE_ID, "Versioned"));
        assertThrows(
                IllegalStateException.class,
                () -> documentEngine.updateDocument(
                        new UpdateDocumentCommand(created.id(), "Conflict", created.version() + 99)));
    }

    @Test
    void versionSnapshotIsPersistedOnUpdate() {
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(TYPE_ID, "Snapshot"));
        documentEngine.updateDocument(new UpdateDocumentCommand(created.id(), "Updated title", created.version()));

        assertFalse(documentVersionPort.findByDocumentId(created.id()).isEmpty());
        assertEquals("Snapshot", documentVersionPort.findByDocumentId(created.id()).getFirst().title());
    }

    @Test
    void lifecycleJournalRemainsConsistentAcrossOperations() {
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(TYPE_ID, "Journal"));
        documentEngine.postDocument(created.id());
        documentEngine.unpostDocument(created.id());
        documentEngine.postDocument(created.id());
        documentEngine.closeDocument(created.id());

        List<LifecycleEventType> events = lifecycleJournalPort.findByDocumentId(created.id()).stream()
                .map(entry -> entry.eventType())
                .toList();
        assertEquals(
                List.of(
                        LifecycleEventType.CREATED,
                        LifecycleEventType.POSTED,
                        LifecycleEventType.UNPOSTED,
                        LifecycleEventType.POSTED,
                        LifecycleEventType.CLOSED),
                events);
    }

    @Test
    void closeAllowedWhenProcessorPermits() {
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(TYPE_ID, "Close ok"));
        documentEngine.postDocument(created.id());
        DocumentMetadata closed = documentEngine.closeDocument(created.id());
        assertEquals(DocumentStatus.CLOSED, closed.status());
    }

    @Test
    void closeRejectedWhenProcessorFails() {
        String failType = uniqueType("close.fail");
        ConfigurableDocumentProcessor processor = registerConfigurable(failType);
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(failType, "Close fail"));
        documentEngine.postDocument(created.id());
        processor.failOn(ConfigurableDocumentProcessor.FailurePoint.ON_CLOSE);

        assertThrows(IllegalStateException.class, () -> documentEngine.closeDocument(created.id()));
        assertEquals(DocumentStatus.POSTED, documentEngine.findById(created.id()).orElseThrow().status());
    }

    @Test
    void concurrentPostAttemptsProduceSingleSuccessfulTransition() throws Exception {
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(TYPE_ID, "Concurrent post"));
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await(5, TimeUnit.SECONDS);
                try {
                    documentEngine.postDocument(created.id());
                    successCount.incrementAndGet();
                } catch (RuntimeException ignored) {
                    // expected optimistic locking / state conflicts
                }
                return null;
            }));
        }

        startLatch.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, successCount.get());
        assertEquals(DocumentStatus.POSTED, documentEngine.findById(created.id()).orElseThrow().status());
    }

    @Test
    void concurrentUpdateAttemptsAllowSingleSuccessfulWrite() throws Exception {
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(TYPE_ID, "Concurrent update"));
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            final int suffix = i;
            futures.add(executor.submit(() -> {
                startLatch.await(5, TimeUnit.SECONDS);
                try {
                    documentEngine.updateDocument(new UpdateDocumentCommand(
                            created.id(), "Title-" + suffix, created.version()));
                    successCount.incrementAndGet();
                } catch (RuntimeException ignored) {
                    // expected version conflicts
                }
                return null;
            }));
        }

        startLatch.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, successCount.get());
        assertEquals(created.version() + 1, documentEngine.findById(created.id()).orElseThrow().version());
    }

    private ConfigurableDocumentProcessor registerConfigurable(String typeId) {
        ConfigurableDocumentProcessor processor = new ConfigurableDocumentProcessor(typeId);
        documentEngine.registerProcessor(processor);
        return processor;
    }

    private static String uniqueType(String suffix) {
        return "platform.lifecycle." + suffix + "." + UUID.randomUUID();
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
