package com.tmp.production.application;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.order.api.OrderStatus;
import com.tmp.production.application.document.ProductionLaunchLine;
import com.tmp.production.application.document.ProductionLaunchPayload;
import com.tmp.production.application.document.ProductionLaunchPayloadHolder;
import com.tmp.production.application.document.ProductionLaunchProcessor;
import com.tmp.production.application.port.OrderForProductionQueryPort;
import com.tmp.production.application.port.OrderForProductionQueryPort.ResolvedItemLine;
import com.tmp.production.application.port.OrderForProductionQueryPort.ResolvedOrderForLaunch;
import com.tmp.production.domain.OrderNotEligibleForProductionException;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SpecificationNotAvailableForLaunchException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Application service orchestrating whole-order Production Launch through the Document Engine.
 *
 * <p>Resolves the ACTIVE customer order and all producible items from Order Management exactly once
 * at Launch, freezes {@link ProductionFoundation} per line, and posts one multi-line Launch
 * document.
 */
public final class ProductionLaunchService {

    private final DocumentEngine documentEngine;
    private final ProductionLaunchPayloadHolder payloadHolder;
    private final OrderForProductionQueryPort orderForProductionQuery;
    private final Clock clock;

    public ProductionLaunchService(
            DocumentEngine documentEngine,
            ProductionLaunchPayloadHolder payloadHolder,
            OrderForProductionQueryPort orderForProductionQuery,
            Clock clock) {
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.payloadHolder = Objects.requireNonNull(payloadHolder, "payloadHolder");
        this.orderForProductionQuery =
                Objects.requireNonNull(orderForProductionQuery, "orderForProductionQuery");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Accepts a whole ACTIVE customer order into production.
     *
     * @return the posted document metadata
     */
    public DocumentMetadata launch(LaunchProductionCommand command) {
        Objects.requireNonNull(command, "command");

        SourceOrderId sourceOrderId = SourceOrderId.of(command.sourceOrderId());
        ResolvedOrderForLaunch order =
                orderForProductionQuery
                        .resolveForLaunch(sourceOrderId)
                        .orElseThrow(
                                () ->
                                        new OrderNotEligibleForProductionException(
                                                sourceOrderId, "order not found"));

        if (order.orderStatus() != OrderStatus.ACTIVE) {
            throw new OrderNotEligibleForProductionException(sourceOrderId, order.orderStatus());
        }

        if (order.activeItemCount() == 0) {
            throw new OrderNotEligibleForProductionException(
                    sourceOrderId, "order has no ACTIVE items");
        }

        if (!order.missingSpecificationItemIds().isEmpty()) {
            throw new SpecificationNotAvailableForLaunchException(
                    order.missingSpecificationItemIds().getFirst());
        }

        if (order.lines().isEmpty()) {
            throw new OrderNotEligibleForProductionException(
                    sourceOrderId, "order has no ACTIVE items");
        }

        var launchTimestamp = clock.instant();
        List<ProductionLaunchLine> lines = new ArrayList<>(order.lines().size());
        for (ResolvedItemLine itemLine : order.lines()) {
            ProductionFoundation foundation =
                    ProductionFoundation.freeze(
                            sourceOrderId,
                            itemLine.sourceOrderItemId(),
                            itemLine.specificationId(),
                            launchTimestamp);
            lines.add(
                    new ProductionLaunchLine(
                            foundation, ProductionQuantity.positive(itemLine.orderedQuantity())));
        }

        ProductionLaunchPayload payload =
                new ProductionLaunchPayload(
                        sourceOrderId, lines, command.createdBy(), launchTimestamp);

        DocumentMetadata draft =
                documentEngine.createDocument(
                        new CreateDocumentCommand(
                                ProductionLaunchProcessor.DOCUMENT_TYPE_ID,
                                "Production Launch: " + command.sourceOrderId()));

        payloadHolder.set(draft.id(), payload);
        try {
            return documentEngine.postDocument(draft.id());
        } catch (RuntimeException ex) {
            payloadHolder.clear(draft.id());
            throw ex;
        }
    }

}
