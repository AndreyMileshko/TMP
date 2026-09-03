package com.tmp.ui.shell.order.worklist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.security.api.AccessDeniedException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItemProductionStateReaderTest {

    @Test
    void readOneEmptyOptionalIsSuccessNotAccepted() {
        FakeProductionQuery query = new FakeProductionQuery();
        ItemProductionReadResult result =
                ItemProductionStateReader.readOne(query, UUID.randomUUID());
        assertInstanceOf(ItemProductionReadResult.SuccessNotAccepted.class, result);
    }

    @Test
    void readOnePresentStateIsSuccessWithState() {
        UUID itemId = UUID.randomUUID();
        ItemProductionStateView state = state(itemId);
        FakeProductionQuery query = new FakeProductionQuery();
        query.single.put(itemId, state);

        ItemProductionReadResult result = ItemProductionStateReader.readOne(query, itemId);

        assertInstanceOf(ItemProductionReadResult.SuccessWithState.class, result);
        assertEquals(state, ((ItemProductionReadResult.SuccessWithState) result).state());
    }

    @Test
    void readOneAccessDeniedIsUnavailable() {
        FakeProductionQuery query = new FakeProductionQuery();
        query.denySingle = true;
        ItemProductionReadResult result =
                ItemProductionStateReader.readOne(query, UUID.randomUUID());
        assertInstanceOf(ItemProductionReadResult.Unavailable.class, result);
    }

    @Test
    void readOneRuntimeExceptionIsUnavailable() {
        FakeProductionQuery query = new FakeProductionQuery();
        query.failSingle = true;
        ItemProductionReadResult result =
                ItemProductionStateReader.readOne(query, UUID.randomUUID());
        assertInstanceOf(ItemProductionReadResult.Unavailable.class, result);
    }

    @Test
    void nullProductionQueryIsUnavailable() {
        ItemProductionReadResult one =
                ItemProductionStateReader.readOne(null, UUID.randomUUID());
        assertInstanceOf(ItemProductionReadResult.Unavailable.class, one);

        ItemProductionStateReader.BatchLoad batch =
                ItemProductionStateReader.loadBatch(null, UUID.randomUUID());
        assertTrue(!batch.available());
        assertInstanceOf(
                ItemProductionReadResult.Unavailable.class,
                ItemProductionStateReader.fromBatch(batch, UUID.randomUUID()));
    }

    @Test
    void batchMissingKeyIsSuccessNotAcceptedPresentIsSuccessWithState() {
        UUID orderId = UUID.randomUUID();
        UUID presentId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        ItemProductionStateView state = state(presentId);
        FakeProductionQuery query = new FakeProductionQuery();
        query.batch.put(presentId, state);

        ItemProductionStateReader.BatchLoad batch =
                ItemProductionStateReader.loadBatch(query, orderId);

        assertTrue(batch.available());
        assertInstanceOf(
                ItemProductionReadResult.SuccessNotAccepted.class,
                ItemProductionStateReader.fromBatch(batch, missingId));
        ItemProductionReadResult present = ItemProductionStateReader.fromBatch(batch, presentId);
        assertInstanceOf(ItemProductionReadResult.SuccessWithState.class, present);
        assertEquals(state, ((ItemProductionReadResult.SuccessWithState) present).state());
    }

    @Test
    void unavailableBatchYieldsUnavailableForEveryLookup() {
        FakeProductionQuery query = new FakeProductionQuery();
        query.denyBatch = true;

        ItemProductionStateReader.BatchLoad batch =
                ItemProductionStateReader.loadBatch(query, UUID.randomUUID());

        assertTrue(!batch.available());
        assertInstanceOf(
                ItemProductionReadResult.Unavailable.class,
                ItemProductionStateReader.fromBatch(batch, UUID.randomUUID()));
    }

    private static ItemProductionStateView state(UUID orderItemId) {
        return new ItemProductionStateView(
                UUID.randomUUID(),
                orderItemId,
                UUID.randomUUID(),
                ItemProductionStateStatus.IN_PRODUCTION,
                10L,
                10L,
                10L,
                0L,
                Optional.empty(),
                Instant.parse("2026-01-01T00:00:00Z"),
                List.of());
    }

    private static final class FakeProductionQuery implements ProductionQueryApi {
        private final Map<UUID, ItemProductionStateView> single = new HashMap<>();
        private final Map<UUID, ItemProductionStateView> batch = new HashMap<>();
        private boolean denySingle;
        private boolean failSingle;
        private boolean denyBatch;

        @Override
        public OrderProductionView getOrderProductionView(UUID orderId) {
            return new OrderProductionView(
                    orderId, OrderProductionViewStatus.NOT_ACCEPTED, 0, 0, 0, 0, 0);
        }

        @Override
        public Optional<ItemProductionStateView> getItemProductionState(UUID orderItemId) {
            if (denySingle) {
                throw new AccessDeniedException("production.item.view");
            }
            if (failSingle) {
                throw new IllegalStateException("boom");
            }
            return Optional.ofNullable(single.get(orderItemId));
        }

        @Override
        public Map<UUID, ItemProductionStateView> getItemProductionStatesByOrderId(UUID orderId) {
            if (denyBatch) {
                throw new AccessDeniedException("production.item.view");
            }
            return Map.copyOf(batch);
        }

        @Override
        public Optional<MaterialAvailabilityResultView> getMaterialAvailabilityResult(UUID orderId) {
            return Optional.empty();
        }

        @Override
        public List<ProductionHistoryEntryView> listProductionHistory(UUID orderId) {
            return List.of();
        }
    }
}
