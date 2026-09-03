package com.tmp.production.domain.repository;

import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.util.List;
import java.util.Optional;

/**
 * Port for persisting item-owned Production state (Production Spec §5).
 *
 * <p>Adapters store already-valid domain state without applying business rules.
 */
public interface ProductionItemStateRepository {

    /** Persists the given state, inserting or updating by business identity. */
    ProductionItemState save(ProductionItemState state);

    /** Loads state by {@code SourceOrderId + SourceOrderItemId + SpecificationId}. */
    Optional<ProductionItemState> findByIdentity(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            SpecificationId specificationId);

    /**
     * Loads all item-owned Production states for the given customer order.
     *
     * <p>Returns an empty list when no Production states exist. Does not invent order-level
     * status; aggregation is performed by the domain calculator.
     */
    List<ProductionItemState> findBySourceOrderId(SourceOrderId sourceOrderId);

    /**
     * Loads item-owned Production states for many orders in one adapter call.
     *
     * <p>Default loops {@link #findBySourceOrderId}. JDBC adapters MUST use a single {@code IN}
     * query. Cutting Plan links may be omitted (empty) because this method is for list
     * aggregation of quantities and status.
     */
    default List<ProductionItemState> findBySourceOrderIds(
            java.util.Collection<SourceOrderId> sourceOrderIds) {
        java.util.Objects.requireNonNull(sourceOrderIds, "sourceOrderIds");
        if (sourceOrderIds.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<ProductionItemState> states = new java.util.ArrayList<>();
        for (SourceOrderId sourceOrderId : sourceOrderIds) {
            states.addAll(findBySourceOrderId(sourceOrderId));
        }
        return List.copyOf(states);
    }

    /**
     * Loads and row-locks (if supported by the adapter) one frozen Production state for the
     * given {@link SourceOrderItemId}.
     *
     * <p>Empty when the item has not been launched (no persisted row).
     *
     * <p>Throws {@link com.tmp.production.persistence.ProductionPersistenceException} when
     * persistence unexpectedly returns multiple states for the same Order Item id.
     */
    default Optional<ProductionItemState> findBySourceOrderItemId(SourceOrderItemId sourceOrderItemId) {
        throw new UnsupportedOperationException(
                "Adapter must implement lookup by SourceOrderItemId: " + sourceOrderItemId);
    }

    /**
     * Loads and row-locks all item-owned Production states for the given customer order.
     *
     * <p>Must be called within an active transaction. Default adapters may delegate to
     * {@link #findBySourceOrderId}; JDBC adapters use {@code SELECT ... FOR UPDATE} with
     * deterministic ordering ({@code sourceOrderItemId}, {@code specificationId}).
     */
    default List<ProductionItemState> findBySourceOrderIdForUpdate(SourceOrderId sourceOrderId) {
        return findBySourceOrderId(sourceOrderId);
    }
}
