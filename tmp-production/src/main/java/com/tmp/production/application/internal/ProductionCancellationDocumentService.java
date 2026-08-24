package com.tmp.production.application.internal;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.production.application.document.ProductionCancellationProcessor;
import com.tmp.production.domain.ProductionCancellation;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.repository.ProductionCancellationRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal Production Cancellation document gateway for {@link
 * com.tmp.production.application.CancelOrderProductionService} orchestration.
 *
 * <p>Not a standalone user-facing cancellation use case. Does not call Warehouse. Joins ambient
 * {@code REQUIRED} transactions; does not use {@code REQUIRES_NEW}.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators injected by the container.")
public final class ProductionCancellationDocumentService {

    private final DocumentEngine documentEngine;
    private final ProductionCancellationRepository cancellationRepository;
    private final Clock clock;

    public ProductionCancellationDocumentService(
            DocumentEngine documentEngine,
            ProductionCancellationRepository cancellationRepository,
            Clock clock) {
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.cancellationRepository =
                Objects.requireNonNull(cancellationRepository, "cancellationRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public DocumentMetadata createDraft(ProductionCancellationDocumentCommand command) {
        Objects.requireNonNull(command, "command");
        Instant cancelledAt =
                command.cancelledAt() != null ? command.cancelledAt() : clock.instant();
        DocumentMetadata draft =
                documentEngine.createDocument(
                        new CreateDocumentCommand(
                                ProductionCancellationProcessor.DOCUMENT_TYPE_ID,
                                "Production Cancellation: " + command.sourceOrderId()));
        ProductionCancellation cancellation =
                ProductionCancellation.draft(
                        draft.id(),
                        SourceOrderId.of(command.sourceOrderId()),
                        cancelledAt,
                        command.reason(),
                        mapItemLines(command.itemLines()));
        cancellationRepository.saveDraft(cancellation);
        return draft;
    }

    @Transactional
    public DocumentMetadata post(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return documentEngine.postDocument(documentId);
    }

    @Transactional(readOnly = true)
    public Optional<ProductionCancellation> findCancellationByDocumentId(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return cancellationRepository.findByDocumentId(documentId);
    }

    private static List<ProductionCancellation.ItemLine> mapItemLines(
            List<ProductionCancellationDocumentCommand.ItemLine> lines) {
        return lines.stream()
                .map(
                        line ->
                                new ProductionCancellation.ItemLine(
                                        line.sourceOrderItemId(),
                                        line.specificationId(),
                                        line.previousStatus(),
                                        line.action(),
                                        line.activeQuantityCancelled(),
                                        line.releasedQuantityPreserved()))
                .toList();
    }

    public record ProductionCancellationDocumentCommand(
            UUID sourceOrderId,
            Instant cancelledAt,
            Optional<String> reason,
            List<ItemLine> itemLines) {

        public ProductionCancellationDocumentCommand {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(itemLines, "itemLines");
            itemLines = List.copyOf(itemLines);
        }

        public record ItemLine(
                com.tmp.production.domain.SourceOrderItemId sourceOrderItemId,
                com.tmp.production.domain.SpecificationId specificationId,
                com.tmp.production.domain.ProductionStatus previousStatus,
                com.tmp.production.domain.CancellationItemAction action,
                com.tmp.production.domain.ProductionQuantity activeQuantityCancelled,
                com.tmp.production.domain.ProductionQuantity releasedQuantityPreserved) {

            public ItemLine {
                Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
                Objects.requireNonNull(specificationId, "specificationId");
                Objects.requireNonNull(previousStatus, "previousStatus");
                Objects.requireNonNull(action, "action");
                Objects.requireNonNull(activeQuantityCancelled, "activeQuantityCancelled");
                Objects.requireNonNull(releasedQuantityPreserved, "releasedQuantityPreserved");
            }
        }
    }
}
