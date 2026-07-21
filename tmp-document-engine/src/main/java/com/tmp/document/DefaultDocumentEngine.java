package com.tmp.document;

import com.tmp.core.api.EventBus;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.component.ComponentType;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.core.api.component.PlatformComponentMetadata;
import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentEngineStatus;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.DocumentTypeDescriptor;
import com.tmp.document.api.LifecycleEventType;
import com.tmp.document.api.UpdateDocumentCommand;
import com.tmp.document.api.event.DocumentClosedEvent;
import com.tmp.document.api.event.DocumentCreatedEvent;
import com.tmp.document.api.event.DocumentDeletedEvent;
import com.tmp.document.api.event.DocumentPostedEvent;
import com.tmp.document.api.event.DocumentUnpostedEvent;
import com.tmp.document.api.port.DocumentStoragePort;
import com.tmp.document.api.port.DocumentVersionPort;
import com.tmp.document.api.port.DocumentVersionSnapshot;
import com.tmp.document.api.port.LifecycleJournalPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Constructor stores Spring-managed port dependencies injected by the container.")
public class DefaultDocumentEngine implements DocumentEngine, PlatformComponent {

    private static final PlatformComponentMetadata METADATA = new PlatformComponentMetadata(
            "document-engine",
            "Document Engine",
            "0.1.0-SNAPSHOT",
            ComponentType.SERVICE);

    private final DefaultDocumentProcessorRegistry processorRegistry;
    private final DocumentStoragePort documentStorage;
    private final LifecycleJournalPort lifecycleJournal;
    private final DocumentVersionPort documentVersionPort;
    private EventBus eventBus;

    public DefaultDocumentEngine(
            DefaultDocumentProcessorRegistry processorRegistry,
            DocumentStoragePort documentStorage,
            LifecycleJournalPort lifecycleJournal,
            DocumentVersionPort documentVersionPort) {
        this.processorRegistry = processorRegistry;
        this.documentStorage = documentStorage;
        this.lifecycleJournal = lifecycleJournal;
        this.documentVersionPort = documentVersionPort;
    }

    @Override
    public PlatformComponentMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(PlatformCore platformCore) {
        this.eventBus = platformCore.eventBus();
    }

    @Override
    public void start() {
        // no-op
    }

    @Override
    public void stop() {
        // no-op
    }

    @Override
    public void registerProcessor(DocumentProcessor processor) {
        processorRegistry.register(processor);
        documentStorage.registerDocumentType(
                processor.documentTypeId(),
                processor.documentTypeId(),
                "Registered document processor");
    }

    @Override
    public DocumentMetadata createDocument(CreateDocumentCommand command) {
        DocumentProcessor processor = processorRegistry.require(command.documentTypeId());
        Instant now = Instant.now();
        DocumentMetadata draft = new DocumentMetadata(
                UUID.randomUUID(),
                command.documentTypeId(),
                DocumentNumberGenerator.nextNumber(command.documentTypeId()),
                command.title(),
                DocumentStatus.DRAFT,
                0L,
                now,
                now,
                null,
                null);
        DocumentOperationContext context = new DocumentOperationContextImpl(draft);
        processor.validateCreate(context);
        DocumentMetadata saved = documentStorage.insert(draft);
        lifecycleJournal.append(
                saved.id(),
                LifecycleEventType.CREATED,
                null,
                DocumentStatus.DRAFT,
                "Document created");
        publishAfterCommit(new DocumentCreatedEvent(saved.id(), saved.documentTypeId()));
        return saved;
    }

    @Override
    public DocumentMetadata updateDocument(UpdateDocumentCommand command) {
        DocumentMetadata existing = requireDocument(command.documentId());
        requireDraft(existing);
        DocumentProcessor processor = processorRegistry.require(existing.documentTypeId());
        DocumentMetadata updated = new DocumentMetadata(
                existing.id(),
                existing.documentTypeId(),
                existing.documentNumber(),
                command.title(),
                existing.status(),
                existing.version() + 1,
                existing.createdAt(),
                Instant.now(),
                existing.postedAt(),
                existing.closedAt());
        processor.validateUpdate(new DocumentOperationContextImpl(updated));
        documentVersionPort.saveSnapshot(new DocumentVersionSnapshot(
                UUID.randomUUID(),
                existing.id(),
                existing.version(),
                existing.title(),
                existing.updatedAt()));
        DocumentMetadata saved = documentStorage.update(updated, command.expectedVersion());
        lifecycleJournal.append(
                saved.id(),
                LifecycleEventType.UPDATED,
                DocumentStatus.DRAFT,
                DocumentStatus.DRAFT,
                "Document updated");
        return saved;
    }

    @Override
    public DocumentMetadata postDocument(UUID documentId) {
        DocumentMetadata existing = requireDocument(documentId);
        requireDraft(existing);
        DocumentProcessor processor = processorRegistry.require(existing.documentTypeId());
        DocumentOperationContext context = new DocumentOperationContextImpl(existing);
        processor.onPost(context);
        DocumentMetadata posted = new DocumentMetadata(
                existing.id(),
                existing.documentTypeId(),
                existing.documentNumber(),
                existing.title(),
                DocumentStatus.POSTED,
                existing.version() + 1,
                existing.createdAt(),
                Instant.now(),
                Instant.now(),
                existing.closedAt());
        DocumentMetadata saved = documentStorage.update(posted, existing.version());
        lifecycleJournal.append(
                saved.id(),
                LifecycleEventType.POSTED,
                DocumentStatus.DRAFT,
                DocumentStatus.POSTED,
                "Document posted");
        publishAfterCommit(new DocumentPostedEvent(saved.id(), saved.documentTypeId()));
        return saved;
    }

    @Override
    public DocumentMetadata unpostDocument(UUID documentId) {
        DocumentMetadata existing = requireDocument(documentId);
        requirePosted(existing);
        DocumentProcessor processor = processorRegistry.require(existing.documentTypeId());
        processor.onUnpost(new DocumentOperationContextImpl(existing));
        DocumentMetadata unposted = new DocumentMetadata(
                existing.id(),
                existing.documentTypeId(),
                existing.documentNumber(),
                existing.title(),
                DocumentStatus.DRAFT,
                existing.version() + 1,
                existing.createdAt(),
                Instant.now(),
                null,
                existing.closedAt());
        DocumentMetadata saved = documentStorage.update(unposted, existing.version());
        lifecycleJournal.append(
                saved.id(),
                LifecycleEventType.UNPOSTED,
                DocumentStatus.POSTED,
                DocumentStatus.DRAFT,
                "Document unposted");
        publishAfterCommit(new DocumentUnpostedEvent(saved.id(), saved.documentTypeId()));
        return saved;
    }

    @Override
    public DocumentMetadata closeDocument(UUID documentId) {
        DocumentMetadata existing = requireDocument(documentId);
        requirePosted(existing);
        DocumentProcessor processor = processorRegistry.require(existing.documentTypeId());
        processor.onClose(new DocumentOperationContextImpl(existing));
        DocumentMetadata closed = new DocumentMetadata(
                existing.id(),
                existing.documentTypeId(),
                existing.documentNumber(),
                existing.title(),
                DocumentStatus.CLOSED,
                existing.version() + 1,
                existing.createdAt(),
                Instant.now(),
                existing.postedAt(),
                Instant.now());
        DocumentMetadata saved = documentStorage.update(closed, existing.version());
        lifecycleJournal.append(
                saved.id(),
                LifecycleEventType.CLOSED,
                DocumentStatus.POSTED,
                DocumentStatus.CLOSED,
                "Document closed");
        publishAfterCommit(new DocumentClosedEvent(saved.id(), saved.documentTypeId()));
        return saved;
    }

    @Override
    public void deleteDocument(UUID documentId) {
        DocumentMetadata existing = requireDocument(documentId);
        requireDraft(existing);
        DocumentProcessor processor = processorRegistry.require(existing.documentTypeId());
        processor.onDelete(new DocumentOperationContextImpl(existing));
        documentStorage.delete(documentId);
        lifecycleJournal.append(
                documentId,
                LifecycleEventType.DELETED,
                DocumentStatus.DRAFT,
                DocumentStatus.DRAFT,
                "Document deleted");
        publishAfterCommit(new DocumentDeletedEvent(documentId, existing.documentTypeId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentMetadata> findById(UUID documentId) {
        return documentStorage.findById(documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentMetadata> search(DocumentQuery query) {
        return documentStorage.search(query);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentTypeDescriptor> registeredTypes() {
        return documentStorage.listDocumentTypes();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentEngineStatus status() {
        return new DocumentEngineStatus(processorRegistry.size(), documentStorage.countAll());
    }

    private DocumentMetadata requireDocument(UUID documentId) {
        return documentStorage.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
    }

    private static void requireDraft(DocumentMetadata document) {
        if (document.status() != DocumentStatus.DRAFT) {
            throw new IllegalStateException("Operation requires DRAFT status: " + document.id());
        }
    }

    private static void requirePosted(DocumentMetadata document) {
        if (document.status() != DocumentStatus.POSTED) {
            throw new IllegalStateException("Operation requires POSTED status: " + document.id());
        }
    }

    private void publishAfterCommit(com.tmp.core.api.event.DomainEvent event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }
}
