package com.tmp.ui.shell.order.worklist;

import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.security.api.AccessDeniedException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared Production item-state read adapter for list and card. Maps Public Query outcomes to
 * {@link ItemProductionReadResult} without inventing manufactured facts.
 */
public final class ItemProductionStateReader {

    private static final Logger LOGGER = System.getLogger(ItemProductionStateReader.class.getName());

    private ItemProductionStateReader() {}

    /** Single-item read for the item card. */
    public static ItemProductionReadResult readOne(ProductionQueryApi productionQuery, UUID orderItemId) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        if (productionQuery == null) {
            return ItemProductionReadResult.unavailable();
        }
        try {
            Optional<ItemProductionStateView> loaded = productionQuery.getItemProductionState(orderItemId);
            return loaded
                    .map(ItemProductionReadResult::successWithState)
                    .orElseGet(ItemProductionReadResult::successNotAccepted);
        } catch (AccessDeniedException ex) {
            LOGGER.log(Level.WARNING, "Production facts unavailable (access denied) for item " + orderItemId, ex);
            return ItemProductionReadResult.unavailable();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Production facts unavailable for item " + orderItemId, ex);
            return ItemProductionReadResult.unavailable();
        }
    }

    /**
     * One batch read for the visible item-list page. On failure returns {@link
     * ItemProductionReadResult#unavailable()} for every lookup via {@link #fromBatch}.
     */
    public static BatchLoad loadBatch(ProductionQueryApi productionQuery, UUID orderId) {
        Objects.requireNonNull(orderId, "orderId");
        if (productionQuery == null) {
            return BatchLoad.unavailable();
        }
        try {
            Map<UUID, ItemProductionStateView> states =
                    productionQuery.getItemProductionStatesByOrderId(orderId);
            return BatchLoad.ok(states == null ? Map.of() : Map.copyOf(states));
        } catch (AccessDeniedException ex) {
            LOGGER.log(
                    Level.WARNING,
                    "Production item batch unavailable (access denied) for order " + orderId,
                    ex);
            return BatchLoad.unavailable();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Production item batch unavailable for order " + orderId, ex);
            return BatchLoad.unavailable();
        }
    }

    public static ItemProductionReadResult fromBatch(BatchLoad batch, UUID orderItemId) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(orderItemId, "orderItemId");
        if (!batch.available()) {
            return ItemProductionReadResult.unavailable();
        }
        ItemProductionStateView state = batch.states().get(orderItemId);
        if (state == null) {
            return ItemProductionReadResult.successNotAccepted();
        }
        return ItemProductionReadResult.successWithState(state);
    }

    /** Result of one order-scoped Production batch read for the current page. */
    public static final class BatchLoad {
        private final boolean available;
        private final Map<UUID, ItemProductionStateView> states;

        private BatchLoad(boolean available, Map<UUID, ItemProductionStateView> states) {
            this.available = available;
            this.states = states;
        }

        public static BatchLoad ok(Map<UUID, ItemProductionStateView> states) {
            return new BatchLoad(true, Map.copyOf(Objects.requireNonNull(states, "states")));
        }

        public static BatchLoad unavailable() {
            return new BatchLoad(false, Map.of());
        }

        public boolean available() {
            return available;
        }

        public Map<UUID, ItemProductionStateView> states() {
            return states;
        }
    }
}
