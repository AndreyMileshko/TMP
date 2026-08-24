package com.tmp.production.application.document;

import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.production.application.event.ProductionReleased;
import com.tmp.production.domain.InvalidProductionStateException;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionRelease;
import com.tmp.production.domain.ProductionReleaseValidationException;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import com.tmp.production.domain.repository.ProductionReleaseRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Document Engine processor for Production Release (Production Spec §9 / §15).
 *
 * <p>Loads durable Production-owned payload, fully pre-validates all item lines, then applies
 * {@link ProductionItemState#release}. Does not call Warehouse, Order Management, or Cutting.
 * Publishes {@link ProductionReleased} after successful POST via {@link TransactionalEventPublisher}.
 */
public final class ProductionReleaseProcessor implements DocumentProcessor {

    public static final String DOCUMENT_TYPE_ID = "production.release";

    private final ProductionReleaseRepository releaseRepository;
    private final ProductionItemStateRepository itemStateRepository;
    private final TransactionalEventPublisher eventPublisher;

    public ProductionReleaseProcessor(
            ProductionReleaseRepository releaseRepository,
            ProductionItemStateRepository itemStateRepository,
            TransactionalEventPublisher eventPublisher) {
        this.releaseRepository = Objects.requireNonNull(releaseRepository, "releaseRepository");
        this.itemStateRepository =
                Objects.requireNonNull(itemStateRepository, "itemStateRepository");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
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
        ProductionRelease release =
                releaseRepository
                        .findByDocumentId(documentId)
                        .orElseThrow(
                                () ->
                                        new ProductionReleaseValidationException(
                                                "Production Release payload not found for document "
                                                        + documentId));
        if (release.posted()) {
            throw new ProductionReleaseValidationException(
                    "Production Release already posted: " + documentId);
        }
        if (!DOCUMENT_TYPE_ID.equals(context.document().documentTypeId())) {
            throw new ProductionReleaseValidationException(
                    "Unexpected document type for Production Release: "
                            + context.document().documentTypeId());
        }

        List<ValidatedLine> validated = preValidateAll(release);
        validateMaterialLineMembership(release);

        for (ValidatedLine line : validated) {
            itemStateRepository.save(line.releasedState());
        }
        releaseRepository.markPosted(release.markPosted());
        eventPublisher.publishAfterCommit(buildReleasedEvent(release, documentId, validated));
    }

    @Override
    public void onUnpost(DocumentOperationContext context) {
        throw new UnsupportedOperationException("Production Release does not support UNPOST");
    }

    @Override
    public void onClose(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
    }

    @Override
    public void onDelete(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
    }

    private List<ValidatedLine> preValidateAll(ProductionRelease release) {
        List<ValidatedLine> validated = new ArrayList<>(release.itemLines().size());
        for (ProductionRelease.ItemLine line : release.itemLines()) {
            validated.add(validateLine(release, line));
        }
        return validated;
    }

    private ValidatedLine validateLine(ProductionRelease release, ProductionRelease.ItemLine line) {
        Optional<ProductionItemState> found =
                itemStateRepository.findByIdentity(
                        release.sourceOrderId(),
                        line.sourceOrderItemId(),
                        line.specificationId());
        if (found.isEmpty()) {
            // Distinguish missing state vs specification mismatch on same item.
            Optional<ProductionItemState> byOrderItem =
                    findByOrderAndItem(release, line.sourceOrderItemId());
            if (byOrderItem.isPresent()) {
                SpecificationId frozen = byOrderItem.orElseThrow().specificationId();
                throw new ProductionReleaseValidationException(
                        "Release SpecificationId does not match frozen Production Foundation: "
                                + "expected="
                                + frozen.value()
                                + ", actual="
                                + line.specificationId().value());
            }
            throw new ProductionReleaseValidationException(
                    "Production item state not found for release line: orderItemId="
                            + line.sourceOrderItemId().value());
        }
        ProductionItemState state = found.orElseThrow();
        if (!state.sourceOrderId().equals(release.sourceOrderId())) {
            throw new ProductionReleaseValidationException(
                    "Release sourceOrderId does not match item state order");
        }
        if (state.status() != ProductionStatus.IN_PRODUCTION
                && state.status() != ProductionStatus.PARTIALLY_RELEASED) {
            throw new ProductionReleaseValidationException(
                    "Release rejected for status " + state.status() + " on item "
                            + line.sourceOrderItemId().value());
        }
        try {
            ProductionItemState released =
                    state.release(line.releaseQuantity(), release.releasedAt());
            return new ValidatedLine(released);
        } catch (InvalidProductionStateException | IllegalArgumentException ex) {
            throw new ProductionReleaseValidationException(
                    "Release quantity validation failed for item "
                            + line.sourceOrderItemId().value()
                            + ": "
                            + ex.getMessage(),
                    ex);
        }
    }

    private static void validateMaterialLineMembership(ProductionRelease release) {
        Set<SourceOrderItemId> itemIds = new HashSet<>();
        for (ProductionRelease.ItemLine itemLine : release.itemLines()) {
            itemIds.add(itemLine.sourceOrderItemId());
        }
        for (ProductionRelease.MaterialLine materialLine : release.materialLines()) {
            materialLine
                    .sourceOrderItemId()
                    .ifPresent(
                            itemId -> {
                                if (!itemIds.contains(itemId)) {
                                    throw new ProductionReleaseValidationException(
                                            "Production Release material line references item not"
                                                    + " in release: "
                                                    + itemId.value());
                                }
                            });
        }
    }

    private Optional<ProductionItemState> findByOrderAndItem(
            ProductionRelease release, SourceOrderItemId itemId) {
        return itemStateRepository.findBySourceOrderId(release.sourceOrderId()).stream()
                .filter(state -> state.sourceOrderItemId().equals(itemId))
                .findFirst();
    }

    private ProductionReleased buildReleasedEvent(
            ProductionRelease release, UUID documentId, List<ValidatedLine> validated) {
        List<ProductionReleased.ReleasedItem> releasedItems =
                new ArrayList<>(release.itemLines().size());
        for (int index = 0; index < release.itemLines().size(); index++) {
            ProductionRelease.ItemLine line = release.itemLines().get(index);
            ProductionItemState resultingState = validated.get(index).releasedState();
            releasedItems.add(
                    new ProductionReleased.ReleasedItem(
                            line.sourceOrderItemId().value(),
                            line.specificationId().value(),
                            line.releaseQuantity().value().longValueExact(),
                            resultingState.status()));
        }
        return new ProductionReleased(
                UUID.randomUUID().toString(),
                release.releasedAt(),
                documentId,
                release.sourceOrderId().value(),
                releasedItems);
    }

    private record ValidatedLine(ProductionItemState releasedState) {}
}
