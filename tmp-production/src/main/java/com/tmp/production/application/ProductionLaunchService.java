package com.tmp.production.application;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.production.application.document.ProductionLaunchPayload;
import com.tmp.production.application.document.ProductionLaunchPayloadHolder;
import com.tmp.production.application.document.ProductionLaunchProcessor;
import com.tmp.production.application.port.OrderSpecificationQueryPort;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedSpecification;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationNotAvailableForLaunchException;
import java.time.Clock;
import java.util.Objects;

/**
 * Application service orchestrating Production Launch through the Document Engine.
 *
 * <p>Resolves the current immutable specification from Order Management exactly once at Launch,
 * freezes the {@link ProductionFoundation}, and posts the Launch document. Post-launch reads must
 * use {@link ProductionFoundationQueryService} with {@code getSpecificationById} semantics only.
 */
public final class ProductionLaunchService {

    private final DocumentEngine documentEngine;
    private final ProductionLaunchPayloadHolder payloadHolder;
    private final OrderSpecificationQueryPort specificationQuery;
    private final Clock clock;

    public ProductionLaunchService(
            DocumentEngine documentEngine,
            ProductionLaunchPayloadHolder payloadHolder,
            OrderSpecificationQueryPort specificationQuery,
            Clock clock) {
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.payloadHolder = Objects.requireNonNull(payloadHolder, "payloadHolder");
        this.specificationQuery = Objects.requireNonNull(specificationQuery, "specificationQuery");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Launches production for a single order item by freezing the current specification and posting
     * a Launch document.
     *
     * @return the posted document metadata
     */
    public DocumentMetadata launch(LaunchProductionCommand command) {
        Objects.requireNonNull(command, "command");

        SourceOrderItemId sourceOrderItemId = SourceOrderItemId.of(command.sourceOrderItemId());
        ResolvedSpecification currentSpecification =
                specificationQuery
                        .resolveCurrentForLaunch(sourceOrderItemId)
                        .orElseThrow(
                                () ->
                                        new SpecificationNotAvailableForLaunchException(
                                                sourceOrderItemId));

        var frozenAt = clock.instant();
        ProductionFoundation foundation =
                ProductionFoundation.freeze(
                        SourceOrderId.of(command.sourceOrderId()),
                        sourceOrderItemId,
                        currentSpecification.specificationId(),
                        frozenAt);

        ProductionLaunchPayload payload =
                new ProductionLaunchPayload(
                        foundation,
                        ProductionQuantity.positive(command.orderedQuantity()),
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
