package com.tmp.production.application.document;

import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.production.application.ProductionHistoryService;
import com.tmp.production.application.event.OrderAcceptedIntoProduction;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionLaunchConflictException;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Document Engine processor for whole-order Production Launch (Production Spec §9).
 *
 * <p>The only component permitted to interact with {@link ProductionItemStateRepository}. All
 * business rules are delegated to {@link ProductionItemState#launch}. Order Management queries are
 * performed before payload creation in {@link com.tmp.production.application.ProductionLaunchService}.
 */
public final class ProductionLaunchProcessor implements DocumentProcessor {

    public static final String DOCUMENT_TYPE_ID = "production.launch";

    private final ProductionItemStateRepository repository;
    private final TransactionalEventPublisher eventPublisher;
    private final ProductionLaunchPayloadHolder payloadHolder;
    private final ProductionHistoryService historyService;

    public ProductionLaunchProcessor(
            ProductionItemStateRepository repository,
            TransactionalEventPublisher eventPublisher,
            ProductionLaunchPayloadHolder payloadHolder,
            ProductionHistoryService historyService) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.payloadHolder = Objects.requireNonNull(payloadHolder, "payloadHolder");
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
        ProductionLaunchPayload payload = payloadHolder.require(context.document().id());

        for (ProductionLaunchLine line : payload.lines()) {
            var foundation = line.foundation();
            if (repository
                    .findByIdentity(
                            foundation.sourceOrderId(),
                            foundation.sourceOrderItemId(),
                            foundation.specificationId())
                    .isPresent()) {
                throw new ProductionLaunchConflictException(
                        foundation.sourceOrderId(),
                        foundation.sourceOrderItemId(),
                        foundation.specificationId());
            }
        }

        List<UUID> acceptedItemIds = new ArrayList<>(payload.lines().size());
        for (ProductionLaunchLine line : payload.lines()) {
            var foundation = line.foundation();
            ProductionItemState state =
                    ProductionItemState.launch(
                            foundation,
                            line.orderedQuantity(),
                            payload.launchTimestamp(),
                            line.cuttingPlanLinks());
            repository.save(state);
            acceptedItemIds.add(foundation.sourceOrderItemId().value());
        }

        historyService.append(
                historyService.orderAccepted(
                        payload.sourceOrderId(),
                        context.document().id(),
                        payload.launchTimestamp(),
                        acceptedItemIds,
                        Optional.of(payload.createdBy())));

        eventPublisher.publishAfterCommit(
                new OrderAcceptedIntoProduction(
                        UUID.randomUUID().toString(),
                        payload.launchTimestamp(),
                        payload.sourceOrderId().value(),
                        acceptedItemIds.size(),
                        acceptedItemIds));
    }

    @Override
    public void onUnpost(DocumentOperationContext context) {
        throw new UnsupportedOperationException("Production Launch does not support UNPOST");
    }

    @Override
    public void onClose(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
    }

    @Override
    public void onDelete(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
    }
}
