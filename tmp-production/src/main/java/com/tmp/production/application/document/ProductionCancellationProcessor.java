package com.tmp.production.application.document;

import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.production.application.ProductionHistoryService;
import com.tmp.production.application.event.OrderProductionCancelled;
import com.tmp.production.domain.CancellationItemAction;
import com.tmp.production.domain.InvalidProductionStateException;
import com.tmp.production.domain.ProductionCancellation;
import com.tmp.production.domain.ProductionCancellationValidationException;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionCancellationRepository;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Document Engine processor for whole-order Production Cancellation (Production Spec §16).
 *
 * <p>Loads durable Production-owned payload, fully pre-validates all item lines, cancels unfinished
 * items and preserves RELEASED items. Does not call Warehouse, Order Management, or Cutting.
 */
public final class ProductionCancellationProcessor implements DocumentProcessor {

    public static final String DOCUMENT_TYPE_ID = "production.cancellation";

    private final ProductionCancellationRepository cancellationRepository;
    private final ProductionItemStateRepository itemStateRepository;
    private final TransactionalEventPublisher eventPublisher;
    private final ProductionHistoryService historyService;

    public ProductionCancellationProcessor(
            ProductionCancellationRepository cancellationRepository,
            ProductionItemStateRepository itemStateRepository,
            TransactionalEventPublisher eventPublisher,
            ProductionHistoryService historyService) {
        this.cancellationRepository =
                Objects.requireNonNull(cancellationRepository, "cancellationRepository");
        this.itemStateRepository =
                Objects.requireNonNull(itemStateRepository, "itemStateRepository");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.historyService = Objects.requireNonNull(historyService, "historyService");
    }

    @Override
    public String documentTypeId() {
        return DOCUMENT_TYPE_ID;
    }

    @Override
    public void validateCreate(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
    }

    @Override
    public void validateUpdate(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
    }

    @Override
    public void onPost(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
        var documentId = context.document().id();
        ProductionCancellation cancellation =
                cancellationRepository
                        .findByDocumentId(documentId)
                        .orElseThrow(
                                () ->
                                        new ProductionCancellationValidationException(
                                                "Production Cancellation payload not found for"
                                                        + " document "
                                                        + documentId));
        if (cancellation.posted()) {
            throw new ProductionCancellationValidationException(
                    "Production Cancellation already posted: " + documentId);
        }
        if (!DOCUMENT_TYPE_ID.equals(context.document().documentTypeId())) {
            throw new ProductionCancellationValidationException(
                    "Unexpected document type for Production Cancellation: "
                            + context.document().documentTypeId());
        }

        List<ValidatedLine> validated = preValidateAll(cancellation);
        for (ValidatedLine line : validated) {
            if (line.mutatedState() != null) {
                itemStateRepository.save(line.mutatedState());
            }
        }
        cancellationRepository.markPosted(cancellation.markPosted());
        historyService.append(historyService.productionCancelled(cancellation, documentId));
        eventPublisher.publishAfterCommit(buildCancelledEvent(cancellation, documentId));
    }

    @Override
    public void onUnpost(DocumentOperationContext context) {
        throw new UnsupportedOperationException("Production Cancellation does not support UNPOST");
    }

    @Override
    public void onClose(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
    }

    @Override
    public void onDelete(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
    }

    private List<ValidatedLine> preValidateAll(ProductionCancellation cancellation) {
        validateItemLineMembership(cancellation);
        List<ValidatedLine> validated = new ArrayList<>(cancellation.itemLines().size());
        for (ProductionCancellation.ItemLine line : cancellation.itemLines()) {
            validated.add(validateLine(cancellation, line));
        }
        return validated;
    }

    private ValidatedLine validateLine(
            ProductionCancellation cancellation, ProductionCancellation.ItemLine line) {
        Optional<ProductionItemState> found =
                itemStateRepository.findByIdentity(
                        cancellation.sourceOrderId(),
                        line.sourceOrderItemId(),
                        line.specificationId());
        if (found.isEmpty()) {
            Optional<ProductionItemState> byOrderItem =
                    findByOrderAndItem(cancellation, line.sourceOrderItemId());
            if (byOrderItem.isPresent()) {
                SpecificationId frozen = byOrderItem.orElseThrow().specificationId();
                throw new ProductionCancellationValidationException(
                        "Cancellation SpecificationId does not match frozen Production Foundation:"
                                + " expected="
                                + frozen.value()
                                + ", actual="
                                + line.specificationId().value());
            }
            throw new ProductionCancellationValidationException(
                    "Production item state not found for cancellation line: orderItemId="
                            + line.sourceOrderItemId().value());
        }
        ProductionItemState state = found.orElseThrow();
        if (!state.sourceOrderId().equals(cancellation.sourceOrderId())) {
            throw new ProductionCancellationValidationException(
                    "Cancellation sourceOrderId does not match item state order");
        }
        if (state.status() != line.previousStatus()) {
            throw new ProductionCancellationValidationException(
                    "Cancellation previousStatus mismatch for item "
                            + line.sourceOrderItemId().value()
                            + ": expected="
                            + line.previousStatus()
                            + ", actual="
                            + state.status());
        }
        if (line.action() == CancellationItemAction.PRESERVED_RELEASED) {
            if (state.status() != ProductionStatus.RELEASED) {
                throw new ProductionCancellationValidationException(
                        "PRESERVED_RELEASED requires RELEASED status on item "
                                + line.sourceOrderItemId().value());
            }
            return new ValidatedLine(null);
        }
        if (state.status() != ProductionStatus.IN_PRODUCTION
                && state.status() != ProductionStatus.PARTIALLY_RELEASED) {
            throw new ProductionCancellationValidationException(
                    "Cancellation rejected for status "
                            + state.status()
                            + " on item "
                            + line.sourceOrderItemId().value());
        }
        try {
            if (!line.activeQuantityCancelled().equals(state.activeProductionQuantity())) {
                throw new ProductionCancellationValidationException(
                        "Active quantity cancelled mismatch for item "
                                + line.sourceOrderItemId().value()
                                + ": expected="
                                + state.activeProductionQuantity()
                                + ", payload="
                                + line.activeQuantityCancelled());
            }
            if (!line.releasedQuantityPreserved().equals(state.releasedQuantity())) {
                throw new ProductionCancellationValidationException(
                        "Released quantity preserved mismatch for item "
                                + line.sourceOrderItemId().value());
            }
            ProductionItemState cancelled = state.cancel(cancellation.cancelledAt());
            if (!cancelled.activeProductionQuantity().isZero()) {
                throw new ProductionCancellationValidationException(
                        "Cancelled item must have zero active quantity: "
                                + line.sourceOrderItemId().value());
            }
            return new ValidatedLine(cancelled);
        } catch (InvalidProductionStateException | IllegalArgumentException ex) {
            throw new ProductionCancellationValidationException(
                    "Cancellation validation failed for item "
                            + line.sourceOrderItemId().value()
                            + ": "
                            + ex.getMessage(),
                    ex);
        }
    }

    private void validateItemLineMembership(ProductionCancellation cancellation) {
        List<ProductionItemState> orderStates =
                itemStateRepository.findBySourceOrderId(cancellation.sourceOrderId());
        if (orderStates.isEmpty()) {
            throw new ProductionCancellationValidationException(
                    "Cancellation rejected: no Production item states for order");
        }
        Set<SourceOrderItemId> payloadItems = new HashSet<>();
        for (ProductionCancellation.ItemLine line : cancellation.itemLines()) {
            payloadItems.add(line.sourceOrderItemId());
        }
        if (payloadItems.size() != cancellation.itemLines().size()) {
            throw new ProductionCancellationValidationException(
                    "Duplicate cancellation item lines in payload");
        }
        if (orderStates.size() != cancellation.itemLines().size()) {
            throw new ProductionCancellationValidationException(
                    "Cancellation must include every Production item for the order");
        }
        for (ProductionItemState state : orderStates) {
            if (!payloadItems.contains(state.sourceOrderItemId())) {
                throw new ProductionCancellationValidationException(
                        "Cancellation missing item line for "
                                + state.sourceOrderItemId().value());
            }
        }
    }

    private Optional<ProductionItemState> findByOrderAndItem(
            ProductionCancellation cancellation, SourceOrderItemId itemId) {
        return itemStateRepository.findBySourceOrderId(cancellation.sourceOrderId()).stream()
                .filter(state -> state.sourceOrderItemId().equals(itemId))
                .findFirst();
    }

    private OrderProductionCancelled buildCancelledEvent(
            ProductionCancellation cancellation, UUID documentId) {
        List<OrderProductionCancelled.ItemOutcome> outcomes =
                cancellation.itemLines().stream()
                        .map(
                                line ->
                                        new OrderProductionCancelled.ItemOutcome(
                                                line.sourceOrderItemId().value(),
                                                line.specificationId().value(),
                                                line.action()))
                        .toList();
        return new OrderProductionCancelled(
                UUID.randomUUID().toString(),
                cancellation.cancelledAt(),
                documentId,
                cancellation.sourceOrderId().value(),
                cancellation.reason(),
                outcomes);
    }

    private record ValidatedLine(ProductionItemState mutatedState) {}
}
