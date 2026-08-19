package com.tmp.production.application.document;

import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.production.application.event.ProductionLaunched;
import com.tmp.production.domain.AlreadyLaunchedForProductionException;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import java.util.Objects;
import java.util.UUID;

/**
 * Document Engine processor for Production Launch (Production Spec §9).
 *
 * <p>The only component permitted to interact with {@link ProductionItemStateRepository}.
 * All business rules are delegated to {@link ProductionItemState#launch}.
 */
public final class ProductionLaunchProcessor implements DocumentProcessor {

    public static final String DOCUMENT_TYPE_ID = "production.launch";

    private final ProductionItemStateRepository repository;
    private final TransactionalEventPublisher eventPublisher;
    private final ProductionLaunchPayloadHolder payloadHolder;

    public ProductionLaunchProcessor(
            ProductionItemStateRepository repository,
            TransactionalEventPublisher eventPublisher,
            ProductionLaunchPayloadHolder payloadHolder) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.payloadHolder = Objects.requireNonNull(payloadHolder, "payloadHolder");
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

        if (repository
                .findByIdentity(
                        payload.sourceOrderId(),
                        payload.sourceOrderItemId(),
                        payload.specificationId())
                .isPresent()) {
            throw new AlreadyLaunchedForProductionException(
                    payload.sourceOrderItemId(), payload.specificationId());
        }

        ProductionItemState state =
                ProductionItemState.launch(
                        payload.sourceOrderId(),
                        payload.sourceOrderItemId(),
                        payload.specificationId(),
                        payload.orderedQuantity(),
                        payload.launchTimestamp());

        repository.save(state);

        eventPublisher.publishAfterCommit(
                new ProductionLaunched(
                        UUID.randomUUID().toString(),
                        payload.launchTimestamp(),
                        payload.sourceOrderId().value(),
                        payload.sourceOrderItemId().value(),
                        payload.specificationId().value(),
                        payload.orderedQuantity().value().longValueExact()));
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
