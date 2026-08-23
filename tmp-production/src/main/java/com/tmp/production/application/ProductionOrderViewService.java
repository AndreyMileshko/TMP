package com.tmp.production.application;

import com.tmp.production.domain.OrderProductionView;
import com.tmp.production.domain.OrderProductionViewCalculator;
import com.tmp.production.domain.OrderProductionViewCalculator.Context;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import java.util.List;
import java.util.Objects;

/**
 * Read-only application query for the computed Order Production View.
 *
 * <p>Does not persist the computed status. Empty Production states yield {@code NOT_ACCEPTED};
 * user-facing {@code AVAILABLE_FOR_PRODUCTION} is composed later with OM ACTIVE confirmation
 * (Public Query / UI adapter), not invented from an empty Production repository result.
 */
public final class ProductionOrderViewService {

    private final ProductionItemStateRepository repository;
    private final OrderProductionViewCalculator calculator;

    public ProductionOrderViewService(ProductionItemStateRepository repository) {
        this(repository, new OrderProductionViewCalculator());
    }

    public ProductionOrderViewService(
            ProductionItemStateRepository repository, OrderProductionViewCalculator calculator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
    }

    /**
     * Computes the Production View from current item-owned states without cancellation context.
     */
    public OrderProductionView getOrderProductionView(SourceOrderId sourceOrderId) {
        return getOrderProductionView(sourceOrderId, Context.none());
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
}
