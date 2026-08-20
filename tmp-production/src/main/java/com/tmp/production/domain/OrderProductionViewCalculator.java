package com.tmp.production.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Pure calculator for the order-level Production View from item-owned states (Spec §5.3).
 *
 * <p>No repositories, OM, Warehouse, Clock, or side effects. Empty state collections yield
 * {@link OrderProductionViewStatus#NOT_ACCEPTED} — never {@code MANUFACTURED} via vacuous
 * {@code allMatch}.
 *
 * <p>{@link Context#wholeOrderCancelled()} is a computed/query input (not a stored Production
 * aggregate). STAGE7-014 will supply it from Production-owned cancellation history/document. Until
 * then, mixes of {@code RELEASED + CANCELLED} without that flag are rejected as ambiguous rather
 * than inventing an order-level cancellation flag.
 */
public final class OrderProductionViewCalculator {

    /**
     * Immutable calculation context. Prefer Production-owned cancellation query in STAGE7-014;
     * do not persist this flag as source of truth.
     */
    public record Context(boolean wholeOrderCancelled) {

        public static Context none() {
            return new Context(false);
        }

        public static Context cancelled() {
            return new Context(true);
        }
    }

    public OrderProductionView calculate(
            SourceOrderId sourceOrderId, Collection<ProductionItemState> states, Context context) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(states, "states");
        Objects.requireNonNull(context, "context");

        if (states.isEmpty()) {
            return new OrderProductionView(
                    sourceOrderId, OrderProductionViewStatus.NOT_ACCEPTED, 0, 0, 0, 0, 0);
        }

        rejectAmbiguousDuplicateItems(states);
        rejectForeignOrderStates(sourceOrderId, states);

        int inProductionCount = 0;
        int partiallyReleasedCount = 0;
        int releasedCount = 0;
        int cancelledCount = 0;
        for (ProductionItemState state : states) {
            switch (state.status()) {
                case IN_PRODUCTION -> inProductionCount++;
                case PARTIALLY_RELEASED -> partiallyReleasedCount++;
                case RELEASED -> releasedCount++;
                case CANCELLED -> cancelledCount++;
                case NOT_STARTED -> throw new InvalidProductionStateException(
                        "Persisted item state must not use NOT_STARTED");
                default -> throw new InvalidProductionStateException(
                        "Unsupported production status in view calculation: " + state.status());
            }
        }

        int itemCount = states.size();
        OrderProductionViewStatus status =
                resolveStatus(
                        context.wholeOrderCancelled(),
                        inProductionCount,
                        partiallyReleasedCount,
                        releasedCount,
                        cancelledCount);

        return new OrderProductionView(
                sourceOrderId,
                status,
                itemCount,
                inProductionCount,
                partiallyReleasedCount,
                releasedCount,
                cancelledCount);
    }

    private static OrderProductionViewStatus resolveStatus(
            boolean wholeOrderCancelled,
            int inProductionCount,
            int partiallyReleasedCount,
            int releasedCount,
            int cancelledCount) {
        boolean hasActive =
                inProductionCount > 0 || partiallyReleasedCount > 0;

        if (wholeOrderCancelled) {
            if (hasActive) {
                throw new InvalidProductionStateException(
                        "Whole-order cancellation context cannot include IN_PRODUCTION or "
                                + "PARTIALLY_RELEASED item states");
            }
            return OrderProductionViewStatus.CANCELLED;
        }

        if (hasActive) {
            return OrderProductionViewStatus.IN_PRODUCTION;
        }

        if (cancelledCount == 0 && releasedCount > 0) {
            return OrderProductionViewStatus.MANUFACTURED;
        }

        if (releasedCount == 0 && cancelledCount > 0) {
            return OrderProductionViewStatus.CANCELLED;
        }

        // RELEASED + CANCELLED without whole-order cancellation context is ambiguous until
        // STAGE7-014 can prove whole-order cancellation from Production-owned history.
        throw new InvalidProductionStateException(
                "Ambiguous RELEASED+CANCELLED mix without whole-order cancellation context; "
                        + "STAGE7-014 must supply Context.cancelled()");
    }

    private static void rejectAmbiguousDuplicateItems(Collection<ProductionItemState> states) {
        Set<SourceOrderItemId> seen = new HashSet<>();
        for (ProductionItemState state : states) {
            if (!seen.add(state.sourceOrderItemId())) {
                throw new InvalidProductionStateException(
                        "Ambiguous duplicate Production item states for SourceOrderItemId "
                                + state.sourceOrderItemId()
                                + "; Production v1.0 does not aggregate multiple active "
                                + "SpecificationId rows for one item");
            }
        }
    }

    private static void rejectForeignOrderStates(
            SourceOrderId sourceOrderId, Collection<ProductionItemState> states) {
        for (ProductionItemState state : states) {
            if (!sourceOrderId.equals(state.sourceOrderId())) {
                throw new IllegalArgumentException(
                        "Production item state belongs to a different SourceOrderId: expected "
                                + sourceOrderId
                                + ", was "
                                + state.sourceOrderId());
            }
        }
    }
}
