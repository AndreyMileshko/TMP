package com.tmp.warehouse.application;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentStatus;
import com.tmp.warehouse.application.document.WarehouseTransferDocumentProcessor;
import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.WarehouseTransferLine;
import com.tmp.warehouse.domain.WarehouseTransferLineId;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.TransferTaskStateRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Application service for Warehouse-owned multi-line Transfer Document foundation (Stage 3.5.2).
 *
 * <p>Creates Document Engine DRAFT + typed payload atomically via {@link TransactionTemplate}.
 * Does not mutate stock.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators.")
public final class WarehouseTransferDocumentService {

    private final DocumentEngine documentEngine;
    private final WarehouseTransferDocumentRepository repository;
    private final TransferTaskStateRepository taskStates;
    private final WarehouseCatalogRepository warehouses;
    private final MaterialReferenceRepository materials;
    private final WarehouseResponsibilityGuard responsibilityGuard;
    private final TransactionTemplate transactionTemplate;

    public WarehouseTransferDocumentService(
            DocumentEngine documentEngine,
            WarehouseTransferDocumentRepository repository,
            WarehouseCatalogRepository warehouses,
            MaterialReferenceRepository materials,
            WarehouseResponsibilityGuard responsibilityGuard,
            TransactionTemplate transactionTemplate) {
        this(
                documentEngine,
                repository,
                NoOpTransferTaskStateRepository.INSTANCE,
                warehouses,
                materials,
                responsibilityGuard,
                transactionTemplate);
    }

    public WarehouseTransferDocumentService(
            DocumentEngine documentEngine,
            WarehouseTransferDocumentRepository repository,
            TransferTaskStateRepository taskStates,
            WarehouseCatalogRepository warehouses,
            MaterialReferenceRepository materials,
            WarehouseResponsibilityGuard responsibilityGuard,
            TransactionTemplate transactionTemplate) {
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.taskStates = Objects.requireNonNull(taskStates, "taskStates");
        this.warehouses = Objects.requireNonNull(warehouses, "warehouses");
        this.materials = Objects.requireNonNull(materials, "materials");
        this.responsibilityGuard =
                Objects.requireNonNull(responsibilityGuard, "responsibilityGuard");
        this.transactionTemplate =
                Objects.requireNonNull(transactionTemplate, "transactionTemplate");
    }

    public CreatedTransferDocument create(CreateCommand command) {
        Objects.requireNonNull(command, "command");
        WarehouseId source = WarehouseId.of(command.sourceWarehouseId());
        WarehouseId destination = WarehouseId.of(command.destinationWarehouseId());
        responsibilityGuard.requireResponsible(source);
        requireWarehouseExists(source);
        requireWarehouseExists(destination);
        if (source.equals(destination)) {
            throw new InvalidWarehouseStateException(
                    "Transfer document requires distinct warehouses: warehouseId=" + source);
        }
        List<WarehouseTransferLine> lines = mapLines(command.lines());

        CreatedTransferDocument created =
                transactionTemplate.execute(
                        status -> {
                            DocumentMetadata draft =
                                    documentEngine.createDocument(
                                            new CreateDocumentCommand(
                                                    WarehouseTransferDocumentProcessor
                                                            .DOCUMENT_TYPE_ID,
                                                    titleFor()));
                            WarehouseTransferDocument payload =
                                    WarehouseTransferDocument.create(
                                            draft.id(), source, destination, lines);
                            repository.insert(payload);
                            return new CreatedTransferDocument(draft, payload);
                        });
        if (created == null) {
            throw new IllegalStateException("Transfer document create returned null");
        }
        return created;
    }

    public LoadedTransferDocument update(UpdateCommand command) {
        Objects.requireNonNull(command, "command");
        LoadedTransferDocument loaded =
                transactionTemplate.execute(
                        status -> {
                            DocumentMetadata metadata = requireDraftDocument(command.documentId());
                            WarehouseTransferDocument existing =
                                    repository
                                            .findByDocumentId(command.documentId())
                                            .orElseThrow(
                                                    () ->
                                                            new IllegalArgumentException(
                                                                    "Transfer document payload not found: "
                                                                            + command
                                                                                    .documentId()));
                            responsibilityGuard.requireResponsible(existing.sourceWarehouseId());

                            WarehouseId newSource = WarehouseId.of(command.sourceWarehouseId());
                            WarehouseId newDestination =
                                    WarehouseId.of(command.destinationWarehouseId());
                            if (!newSource.equals(existing.sourceWarehouseId())) {
                                responsibilityGuard.requireResponsible(newSource);
                            }
                            requireWarehouseExists(newSource);
                            requireWarehouseExists(newDestination);
                            if (newSource.equals(newDestination)) {
                                throw new InvalidWarehouseStateException(
                                        "Transfer document requires distinct warehouses: warehouseId="
                                                + newSource);
                            }

                            List<WarehouseTransferLine> lines = mapLines(command.lines());
                            boolean sourceChanged = !newSource.equals(existing.sourceWarehouseId());
                            WarehouseTransferDocument updated =
                                    existing.withContent(
                                            newSource,
                                            newDestination,
                                            lines,
                                            command.expectedPayloadRevision());
                            repository.update(updated, command.expectedPayloadRevision());
                            if (sourceChanged) {
                                // Stale informational worker for previous source must clear
                                // atomically with the payload update (Stage 3.5.5).
                                taskStates.clear(command.documentId());
                            }
                            WarehouseTransferDocument persisted =
                                    repository
                                            .findByDocumentId(command.documentId())
                                            .orElseThrow(
                                                    () ->
                                                            new IllegalStateException(
                                                                    "Transfer document missing after update: "
                                                                            + command
                                                                                    .documentId()));
                            return new LoadedTransferDocument(metadata, persisted);
                        });
        if (loaded == null) {
            throw new IllegalStateException("Transfer document update returned null");
        }
        return loaded;
    }

    public void delete(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        transactionTemplate.executeWithoutResult(
                status -> {
                    DocumentMetadata metadata = requireDraftDocument(documentId);
                    WarehouseTransferDocument existing =
                            repository
                                    .findByDocumentId(documentId)
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Transfer document payload not found: "
                                                                    + documentId));
                    responsibilityGuard.requireResponsible(existing.sourceWarehouseId());
                    documentEngine.deleteDocument(metadata.id());
                });
    }

    public Optional<LoadedTransferDocument> findByDocumentId(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        Optional<DocumentMetadata> metadata = documentEngine.findById(documentId);
        if (metadata.isEmpty()) {
            return Optional.empty();
        }
        Optional<WarehouseTransferDocument> payload = repository.findByDocumentId(documentId);
        if (payload.isEmpty()) {
            return Optional.empty();
        }
        WarehouseTransferDocument document = payload.orElseThrow();
        requireReadResponsibility(document);
        return Optional.of(new LoadedTransferDocument(metadata.orElseThrow(), document));
    }

    private void requireReadResponsibility(WarehouseTransferDocument document) {
        try {
            responsibilityGuard.requireResponsible(document.sourceWarehouseId());
        } catch (RuntimeException sourceDenied) {
            try {
                responsibilityGuard.requireResponsible(document.destinationWarehouseId());
            } catch (RuntimeException destinationDenied) {
                throw sourceDenied;
            }
        }
    }

    private DocumentMetadata requireDraftDocument(UUID documentId) {
        DocumentMetadata metadata =
                documentEngine
                        .findById(documentId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Document not found: " + documentId));
        if (metadata.status() != DocumentStatus.DRAFT) {
            throw new IllegalStateException(
                    "Transfer document payload is editable only in DRAFT: documentId="
                            + documentId
                            + ", status="
                            + metadata.status());
        }
        return metadata;
    }

    private void requireWarehouseExists(WarehouseId warehouseId) {
        boolean exists =
                warehouses.findAll().stream().anyMatch(w -> w.id().equals(warehouseId));
        if (!exists) {
            throw new IllegalArgumentException("Warehouse not found: " + warehouseId.value());
        }
    }

    private List<WarehouseTransferLine> mapLines(List<LineInput> inputs) {
        List<LineInput> safe = inputs == null ? List.of() : inputs;
        List<WarehouseTransferLine> lines = new ArrayList<>(safe.size());
        int order = 1;
        for (LineInput input : safe) {
            Objects.requireNonNull(input, "line");
            Objects.requireNonNull(input.materialReferenceId(), "materialReferenceId");
            Objects.requireNonNull(input.quantity(), "quantity");
            if (materials.findById(MaterialReferenceId.of(input.materialReferenceId())).isEmpty()) {
                throw new IllegalArgumentException(
                        "Material reference not found: " + input.materialReferenceId());
            }
            WarehouseTransferLineId lineId =
                    input.lineId() == null
                            ? WarehouseTransferLineId.generate()
                            : WarehouseTransferLineId.of(input.lineId());
            int lineOrder = input.lineOrder() != null ? input.lineOrder() : order;
            lines.add(
                    WarehouseTransferLine.of(
                            lineId,
                            MaterialReferenceId.of(input.materialReferenceId()),
                            StockQuantity.of(input.quantity()),
                            lineOrder));
            order++;
        }
        return lines;
    }

    /**
     * Stable Document Engine title — must not embed mutable DRAFT source/destination route
     * (Stage 3.5.2 corrective / 3.5.3).
     */
    private static String titleFor() {
        return "Перемещение материалов";
    }

    public record LineInput(
            UUID lineId, UUID materialReferenceId, BigDecimal quantity, Integer lineOrder) {}

    public record CreateCommand(
            UUID sourceWarehouseId, UUID destinationWarehouseId, List<LineInput> lines) {
        public CreateCommand {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    public record UpdateCommand(
            UUID documentId,
            long expectedPayloadRevision,
            UUID sourceWarehouseId,
            UUID destinationWarehouseId,
            List<LineInput> lines) {
        public UpdateCommand {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    public record CreatedTransferDocument(
            DocumentMetadata metadata, WarehouseTransferDocument payload) {}

    public record LoadedTransferDocument(
            DocumentMetadata metadata, WarehouseTransferDocument payload) {}

    /** No-op for unit tests that never exercise task assignment. */
    private static final class NoOpTransferTaskStateRepository implements TransferTaskStateRepository {
        static final NoOpTransferTaskStateRepository INSTANCE = new NoOpTransferTaskStateRepository();

        @Override
        public Optional<com.tmp.warehouse.domain.TransferTaskAssignment> findByDocumentId(
                UUID documentId) {
            return Optional.empty();
        }

        @Override
        public java.util.Map<UUID, com.tmp.warehouse.domain.TransferTaskAssignment> findByDocumentIds(
                java.util.Collection<UUID> documentIds) {
            return java.util.Map.of();
        }

        @Override
        public com.tmp.warehouse.domain.TransferTaskAssignment takeInWork(
                UUID documentId, UUID workingUserId, java.time.Instant workingSince) {
            throw new UnsupportedOperationException("task state not wired");
        }

        @Override
        public void clear(UUID documentId) {
            // no-op when task persistence is not wired (unit stubs)
        }
    }
}
