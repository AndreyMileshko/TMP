package com.tmp.production.application;

import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentStatus;
import com.tmp.production.application.CancelOrderProductionResult.ItemResult;
import com.tmp.production.application.internal.ProductionCancellationDocumentService;
import com.tmp.production.application.internal.ProductionCancellationDocumentService.ProductionCancellationDocumentCommand;
import com.tmp.production.application.internal.ProductionCancellationDocumentService.ProductionCancellationDocumentCommand.ItemLine;
import com.tmp.production.domain.CancelOrderProductionException;
import com.tmp.production.domain.CancellationItemAction;
import com.tmp.production.domain.OrderProductionViewCalculator;
import com.tmp.production.domain.OrderProductionViewCalculator.Context;
import com.tmp.production.domain.OrderProductionViewStatus;
import com.tmp.production.domain.ProductionCancellationAlreadyExistsException;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.ProductionCancellationQuery;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Application orchestration for whole-order Production Cancellation (Production Spec §16).
 *
 * <p>Locks the order, validates cancellability, creates and POSTs the Production Cancellation
 * document in one outer {@code REQUIRED} transaction. Does not call Warehouse or Cutting.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected collaborators, TX manager and clock.")
public final class CancelOrderProductionService {

    private final ProductionOrderStateLockService stateLockService;
    private final ProductionCancellationQuery cancellationQuery;
    private final ProductionCancellationDocumentService cancellationDocumentService;
    private final OrderProductionViewCalculator viewCalculator;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public CancelOrderProductionService(
            ProductionOrderViewService orderViewService,
            ProductionCancellationQuery cancellationQuery,
            ProductionCancellationDocumentService cancellationDocumentService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this(
                new ProductionOrderStateLockService(orderViewService),
                cancellationQuery,
                cancellationDocumentService,
                new OrderProductionViewCalculator(),
                transactionManager,
                clock);
    }

    CancelOrderProductionService(
            ProductionOrderStateLockService stateLockService,
            ProductionCancellationQuery cancellationQuery,
            ProductionCancellationDocumentService cancellationDocumentService,
            OrderProductionViewCalculator viewCalculator,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.stateLockService = Objects.requireNonNull(stateLockService, "stateLockService");
        this.cancellationQuery =
                Objects.requireNonNull(cancellationQuery, "cancellationQuery");
        this.cancellationDocumentService =
                Objects.requireNonNull(
                        cancellationDocumentService, "cancellationDocumentService");
        this.viewCalculator = Objects.requireNonNull(viewCalculator, "viewCalculator");
        this.transactionTemplate =
                new TransactionTemplate(
                        Objects.requireNonNull(transactionManager, "transactionManager"));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Cancels unfinished Production for the entire order. RELEASED items are preserved.
     */
    public CancelOrderProductionResult cancelOrderProduction(CancelOrderProductionCommand command) {
        Objects.requireNonNull(command, "command");
        CancelOrderProductionResult result =
                transactionTemplate.execute(
                        status -> {
                            SourceOrderId sourceOrderId =
                                    SourceOrderId.of(command.sourceOrderId());
                            List<ProductionItemState> lockedStates =
                                    stateLockService.lockAllItemStates(sourceOrderId);
                            PreparedCancellation prepared =
                                    prepareCancellation(sourceOrderId, lockedStates, command);
                            return executeCancellation(command, prepared);
                        });
        if (result == null) {
            throw new CancelOrderProductionException(
                    "Cancellation orchestration returned null for order "
                            + command.sourceOrderId());
        }
        return result;
    }

    private PreparedCancellation prepareCancellation(
            SourceOrderId sourceOrderId,
            List<ProductionItemState> lockedStates,
            CancelOrderProductionCommand command) {
        if (lockedStates.isEmpty()) {
            throw new CancelOrderProductionException(
                    "Cancellation rejected: order has no Production item states");
        }
        if (cancellationQuery.hasPostedCancellation(sourceOrderId)) {
            throw new ProductionCancellationAlreadyExistsException(sourceOrderId);
        }
        rejectDuplicateItems(lockedStates);
        rejectForeignOrderStates(sourceOrderId, lockedStates);
        if (viewCalculator.calculate(sourceOrderId, lockedStates, Context.none()).status()
                != OrderProductionViewStatus.IN_PRODUCTION) {
            throw new CancelOrderProductionException(
                    "Cancellation is allowed only when order Production View is IN_PRODUCTION");
        }

        Instant cancelledAt = clock.instant();
        List<ItemLine> documentLines = new ArrayList<>(lockedStates.size());
        List<ItemResult> itemResults = new ArrayList<>(lockedStates.size());
        for (ProductionItemState state : lockedStates) {
            validateCancellableStatus(state);
            CancellationItemAction action =
                    state.status() == ProductionStatus.RELEASED
                            ? CancellationItemAction.PRESERVED_RELEASED
                            : CancellationItemAction.CANCELLED_UNFINISHED;
            long activeCancelled =
                    action == CancellationItemAction.CANCELLED_UNFINISHED
                            ? state.activeProductionQuantity().value().longValueExact()
                            : 0L;
            long releasedPreserved = state.releasedQuantity().value().longValueExact();
            documentLines.add(
                    new ItemLine(
                            state.sourceOrderItemId(),
                            state.specificationId(),
                            state.status(),
                            action,
                            state.activeProductionQuantity(),
                            state.releasedQuantity()));
            itemResults.add(
                    new ItemResult(
                            state.sourceOrderItemId().value(),
                            state.specificationId().value(),
                            state.status(),
                            action,
                            activeCancelled,
                            releasedPreserved));
        }
        return new PreparedCancellation(cancelledAt, documentLines, itemResults, command.reason());
    }

    private CancelOrderProductionResult executeCancellation(
            CancelOrderProductionCommand command, PreparedCancellation prepared) {
        DocumentMetadata draft =
                cancellationDocumentService.createDraft(
                        new ProductionCancellationDocumentCommand(
                                command.sourceOrderId(),
                                prepared.cancelledAt(),
                                prepared.reason(),
                                prepared.documentLines()));

        DocumentMetadata posted = cancellationDocumentService.post(draft.id());
        if (posted.status() != DocumentStatus.POSTED) {
            throw new CancelOrderProductionException(
                    "Production Cancellation post did not reach POSTED status: "
                            + posted.status());
        }

        return new CancelOrderProductionResult(
                draft.id(),
                command.sourceOrderId(),
                prepared.cancelledAt(),
                prepared.itemResults());
    }

    private static void validateCancellableStatus(ProductionItemState state) {
        if (state.status() != ProductionStatus.IN_PRODUCTION
                && state.status() != ProductionStatus.PARTIALLY_RELEASED
                && state.status() != ProductionStatus.RELEASED) {
            throw new CancelOrderProductionException(
                    "Cancellation rejected for unsupported status "
                            + state.status()
                            + " on item "
                            + state.sourceOrderItemId().value());
        }
    }

    private static void rejectDuplicateItems(List<ProductionItemState> states) {
        Set<SourceOrderItemId> seen = new HashSet<>();
        for (ProductionItemState state : states) {
            if (!seen.add(state.sourceOrderItemId())) {
                throw new CancelOrderProductionException(
                        "Ambiguous duplicate Production item states for item "
                                + state.sourceOrderItemId().value());
            }
        }
    }

    private static void rejectForeignOrderStates(
            SourceOrderId sourceOrderId, List<ProductionItemState> states) {
        for (ProductionItemState state : states) {
            if (!sourceOrderId.equals(state.sourceOrderId())) {
                throw new CancelOrderProductionException(
                        "Production item state belongs to a different SourceOrderId");
            }
        }
    }

    private record PreparedCancellation(
            Instant cancelledAt,
            List<ItemLine> documentLines,
            List<ItemResult> itemResults,
            java.util.Optional<String> reason) {}
}
