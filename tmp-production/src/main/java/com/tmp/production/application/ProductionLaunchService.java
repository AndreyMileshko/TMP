package com.tmp.production.application;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.production.application.document.ProductionLaunchPayload;
import com.tmp.production.application.document.ProductionLaunchPayloadHolder;
import com.tmp.production.application.document.ProductionLaunchProcessor;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.time.Clock;
import java.util.Objects;

/**
 * Application service orchestrating Production Launch through the Document Engine.
 *
 * <p>Creates a Production Launch document, attaches the payload, and posts it.
 * The {@link ProductionLaunchProcessor} performs the actual domain mutation on post.
 */
public final class ProductionLaunchService {

    private final DocumentEngine documentEngine;
    private final ProductionLaunchPayloadHolder payloadHolder;
    private final Clock clock;

    public ProductionLaunchService(
            DocumentEngine documentEngine,
            ProductionLaunchPayloadHolder payloadHolder,
            Clock clock) {
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.payloadHolder = Objects.requireNonNull(payloadHolder, "payloadHolder");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Launches production for a single order item by creating and posting a Launch document.
     *
     * @return the posted document metadata
     */
    public DocumentMetadata launch(LaunchProductionCommand command) {
        Objects.requireNonNull(command, "command");

        ProductionLaunchPayload payload =
                new ProductionLaunchPayload(
                        SourceOrderId.of(command.sourceOrderId()),
                        SourceOrderItemId.of(command.sourceOrderItemId()),
                        SpecificationId.of(command.specificationId()),
                        ProductionQuantity.positive(command.orderedQuantity()),
                        clock.instant(),
                        command.createdBy());

        DocumentMetadata draft =
                documentEngine.createDocument(
                        new CreateDocumentCommand(
                                ProductionLaunchProcessor.DOCUMENT_TYPE_ID,
                                "Production Launch: " + command.sourceOrderItemId()));

        payloadHolder.set(draft.id(), payload);
        try {
            return documentEngine.postDocument(draft.id());
        } catch (RuntimeException ex) {
            payloadHolder.clear(draft.id());
            throw ex;
        }
    }
}
