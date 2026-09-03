package com.tmp.production.application;

import com.tmp.production.domain.OrderProductionView;
import com.tmp.production.domain.OrderProductionViewCalculator;
import com.tmp.production.domain.OrderProductionViewCalculator.Context;
import com.tmp.production.domain.OrderProductionViewStatus;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.ProductionCancellationQuery;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only application query for the computed Order Production View.
 *
 * <p>Does not persist the computed status. Empty Production states yield {@code NOT_ACCEPTED};
 * user-facing {@code AVAILABLE_FOR_PRODUCTION} is composed later with OM ACTIVE confirmation
 * (Public Query / UI adapter), not invented from an empty Production repository result.
 */
public final class ProductionOrderViewService {

    private final ProductionItemStateRepository repository;
    private final ProductionCancellationQuery cancellationQuery;
    private final OrderProductionViewCalculator calculator;

    public ProductionOrderViewService(ProductionItemStateRepository repository) {
        this(repository, sourceOrderId -> false, new OrderProductionViewCalculator());
    }

    public ProductionOrderViewService(
            ProductionItemStateRepository repository,
            ProductionCancellationQuery cancellationQuery) {
        this(repository, cancellationQuery, new OrderProductionViewCalculator());
    }

    public ProductionOrderViewService(
            ProductionItemStateRepository repository, OrderProductionViewCalculator calculator) {
        this(repository, sourceOrderId -> false, calculator);
    }

    public ProductionOrderViewService(
            ProductionItemStateRepository repository,
            ProductionCancellationQuery cancellationQuery,
            OrderProductionViewCalculator calculator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cancellationQuery =
                Objects.requireNonNull(cancellationQuery, "cancellationQuery");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
    }

    /**
     * Computes the Production View from item-owned states and posted Cancellation evidence.
     */
    public OrderProductionView getOrderProductionView(SourceOrderId sourceOrderId) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Context context =
                cancellationQuery.hasPostedCancellation(sourceOrderId)
                        ? Context.cancelled()
                        : Context.none();
        return getOrderProductionView(sourceOrderId, context);
    }

    /**
     * Computes the Production View. {@code context.wholeOrderCancelled()} is a query input for
     * STAGE7-014; it is not stored by this service.
     */
    public OrderProductionView getOrderProductionView(
            SourceOrderId sourceOrderId, Context context) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(context, "context");
        return calculator.calculate(sourceOrderId, listItemStates(sourceOrderId), context);
    }

    /**
     * Read-only item-owned Production states for the order. Empty when the order has not been
     * launched.
     */
    public List<ProductionItemState> listItemStates(SourceOrderId sourceOrderId) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        return repository.findBySourceOrderId(sourceOrderId);
    }

    /**
     * Row-locks all item-owned Production states for the order within the ambient transaction.
     *
     * <p>Deterministic lock order: {@code sourceOrderItemId}, {@code specificationId}.
     */
    public List<ProductionItemState> lockAllItemStates(SourceOrderId sourceOrderId) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        return repository.findBySourceOrderIdForUpdate(sourceOrderId);
    }

    /**
     * Read-only item-owned Production state for one {@link SourceOrderItemId}.
     *
     * <p>Empty when the order item has not been launched (no persisted row).
     */
    public Optional<ProductionItemState> findItemProductionStateByOrderItemId(
            SourceOrderItemId sourceOrderItemId) {
        Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        return repository.findBySourceOrderItemId(sourceOrderItemId);
    }

    /**
     * Batch Production facts for the operational Orders list. Two SQL operations: item states IN
     * query and posted-cancellation IN query.
     */
    public java.util.Map<java.util.UUID, ProductionListFacts> listProductionListFacts(
            java.util.Collection<java.util.UUID> orderIds) {
        Objects.requireNonNull(orderIds, "orderIds");
        if (orderIds.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.LinkedHashSet<SourceOrderId> sourceIds = new java.util.LinkedHashSet<>();
        for (java.util.UUID orderId : orderIds) {
            Objects.requireNonNull(orderId, "orderId");
            sourceIds.add(SourceOrderId.of(orderId));
        }
        java.util.List<ProductionItemState> states = repository.findBySourceOrderIds(sourceIds);
        java.util.Set<SourceOrderId> cancelledOrders =
                cancellationQuery.findPostedCancellationOrderIds(sourceIds);
        java.util.Map<SourceOrderId, java.util.List<ProductionItemState>> byOrder =
                new java.util.LinkedHashMap<>();
        for (SourceOrderId sourceOrderId : sourceIds) {
            byOrder.put(sourceOrderId, new java.util.ArrayList<>());
        }
        for (ProductionItemState state : states) {
            byOrder.computeIfAbsent(state.sourceOrderId(), key -> new java.util.ArrayList<>()).add(state);
        }
        java.util.Map<java.util.UUID, ProductionListFacts> facts = new java.util.LinkedHashMap<>();
        for (SourceOrderId sourceOrderId : sourceIds) {
            boolean cancellationPosted = cancelledOrders.contains(sourceOrderId);
            Context context = cancellationPosted ? Context.cancelled() : Context.none();
            java.util.List<ProductionItemState> orderStates = byOrder.getOrDefault(sourceOrderId, List.of());
            OrderProductionView view = calculator.calculate(sourceOrderId, orderStates, context);
            long ordered = 0L;
            long released = 0L;
            long active = 0L;
            for (ProductionItemState state : orderStates) {
                ordered += state.orderedQuantity().value().longValueExact();
                released += state.releasedQuantity().value().longValueExact();
                active += state.activeProductionQuantity().value().longValueExact();
            }
            facts.put(
                    sourceOrderId.value(),
                    new ProductionListFacts(
                            sourceOrderId.value(),
                            view.status(),
                            ordered,
                            released,
                            active,
                            cancellationPosted));
        }
        return java.util.Map.copyOf(facts);
    }

    public record ProductionListFacts(
            java.util.UUID sourceOrderId,
            OrderProductionViewStatus status,
            long orderedQuantity,
            long releasedQuantity,
            long activeProductionQuantity,
            boolean cancellationPosted) {}
}
