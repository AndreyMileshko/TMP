package com.tmp.production.application.document;

import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Document Engine processor for Production Release (Production Spec §9 / §15).
 *
 * <p>Loads durable Production-owned payload, fully pre-validates all item lines, then applies
 * {@link ProductionItemState#release}. Does not call Warehouse, Order Management, or Cutting.
 * Domain event {@code ProductionReleased} is deferred to STAGE7-015.
 */
public final class ProductionReleaseProcessor implements DocumentProcessor {

    public static final String DOCUMENT_TYPE_ID = "production.release";

    private final ProductionReleaseRepository releaseRepository;
    private final ProductionItemStateRepository itemStateRepository;

    public ProductionReleaseProcessor(
            ProductionReleaseRepository releaseRepository,
            ProductionItemStateRepository itemStateRepository) {
        this.releaseRepository = Objects.requireNonNull(releaseRepository, "releaseRepository");
        this.itemStateRepository =
                Objects.requireNonNull(itemStateRepository, "itemStateRepository");
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

        for (ValidatedLine line : validated) {
            itemStateRepository.save(line.releasedState());
        }
        releaseRepository.markPosted(release.markPosted());
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

    private Optional<ProductionItemState> findByOrderAndItem(
            ProductionRelease release, SourceOrderItemId itemId) {
        return itemStateRepository.findBySourceOrderId(release.sourceOrderId()).stream()
                .filter(state -> state.sourceOrderItemId().equals(itemId))
                .findFirst();
    }

    private record ValidatedLine(ProductionItemState releasedState) {}
}
