package com.tmp.document;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.port.DocumentStoragePort;
import com.tmp.document.api.port.DocumentVersionPort;
import com.tmp.document.api.port.LifecycleJournalPort;
import com.tmp.document.support.TestDocumentProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class DefaultDocumentEngineRegistrationTest {

    private static final String TYPE_ID = "registration.test";

    private DefaultDocumentProcessorRegistry processorRegistry;
    private DocumentStoragePort documentStorage;
    private LifecycleJournalPort lifecycleJournal;
    private DocumentVersionPort documentVersionPort;
    private TransactionAfterCommitEventPublisher eventPublisher;
    private DefaultDocumentEngine documentEngine;

    @BeforeEach
    void setUp() {
        processorRegistry = new DefaultDocumentProcessorRegistry();
        documentStorage = mock(DocumentStoragePort.class);
        lifecycleJournal = mock(LifecycleJournalPort.class);
        documentVersionPort = mock(DocumentVersionPort.class);
        eventPublisher = new TransactionAfterCommitEventPublisher();
        documentEngine = new DefaultDocumentEngine(
                processorRegistry,
                documentStorage,
                lifecycleJournal,
                documentVersionPort,
                eventPublisher);
    }

    @Test
    void deactivateBlocksNewOperationsWithoutRemovingPersistedDocumentType() {
        DocumentProcessor processor = new TestDocumentProcessor(TYPE_ID);
        when(documentStorage.documentTypeExists(TYPE_ID)).thenReturn(true);
        when(documentStorage.hasDocumentsForType(TYPE_ID)).thenReturn(true);
        DocumentProcessorRegistration registration = documentEngine.registerProcessor(processor);
        registration.deactivate();

        assertFalse(processorRegistry.isActive(TYPE_ID));
        verify(documentStorage).registerDocumentType(TYPE_ID, TYPE_ID, "Registered document processor");
    }

    @Test
    void unregisterRemovesDocumentTypeWhenNoDocumentsExist() {
        DocumentProcessor processor = new TestDocumentProcessor(TYPE_ID);
        when(documentStorage.hasDocumentsForType(TYPE_ID)).thenReturn(false);
        DocumentProcessorRegistration registration = documentEngine.registerProcessor(processor);

        registration.unregister();

        assertFalse(processorRegistry.isRegistered(TYPE_ID));
        verify(documentStorage).unregisterDocumentType(TYPE_ID);
    }

    @Test
    void unregisterRetainsDocumentTypeWhenDocumentsExist() {
        DocumentProcessor processor = new TestDocumentProcessor(TYPE_ID);
        when(documentStorage.hasDocumentsForType(TYPE_ID)).thenReturn(true);
        DocumentProcessorRegistration registration = documentEngine.registerProcessor(processor);

        registration.unregister();

        assertFalse(processorRegistry.isRegistered(TYPE_ID));
        verify(documentStorage, never()).unregisterDocumentType(TYPE_ID);
    }

    @Test
    void duplicateProcessorRegistrationIsRejected() {
        DocumentProcessor processor = new TestDocumentProcessor(TYPE_ID);
        documentEngine.registerProcessor(processor);
        assertThrows(IllegalStateException.class, () -> documentEngine.registerProcessor(processor));
    }

    @Test
    void dbFailureDoesNotLeaveProcessorInRegistry() {
        DocumentProcessor processor = new TestDocumentProcessor(TYPE_ID);
        doThrow(new DataAccessResourceFailureException("simulated DB failure"))
                .when(documentStorage)
                .registerDocumentType(TYPE_ID, TYPE_ID, "Registered document processor");

        assertThrows(
                DataAccessResourceFailureException.class,
                () -> documentEngine.registerProcessor(processor));
        assertFalse(processorRegistry.isRegistered(TYPE_ID));
        verify(documentStorage).registerDocumentType(TYPE_ID, TYPE_ID, "Registered document processor");
    }

    @Test
    void registrationSucceedsAfterPreviousDbFailure() {
        DocumentProcessor processor = new TestDocumentProcessor(TYPE_ID);
        doThrow(new DataAccessResourceFailureException("simulated DB failure"))
                .doNothing()
                .when(documentStorage)
                .registerDocumentType(TYPE_ID, TYPE_ID, "Registered document processor");

        assertThrows(
                DataAccessResourceFailureException.class,
                () -> documentEngine.registerProcessor(processor));
        assertFalse(processorRegistry.isRegistered(TYPE_ID));

        documentEngine.registerProcessor(processor);
        assertTrue(processorRegistry.isRegistered(TYPE_ID));
    }
}
