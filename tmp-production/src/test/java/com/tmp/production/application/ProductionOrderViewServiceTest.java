package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.domain.OrderProductionView;
import com.tmp.production.domain.OrderProductionViewStatus;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class ProductionOrderViewServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-20T09:00:00Z");

    @Test
    void emptyRepositoryYieldsNotAccepted() {
        InMemoryRepository repository = new InMemoryRepository();
        ProductionOrderViewService service = new ProductionOrderViewService(repository);

        OrderProductionView view = service.getOrderProductionView(SourceOrderId.generate());

        assertEquals(OrderProductionViewStatus.NOT_ACCEPTED, view.status());
        assertEquals(0, view.itemCount());
    }

    @Test
    void computesInProductionFromPersistedStates() {
        SourceOrderId orderId = SourceOrderId.generate();
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(launched(orderId, SourceOrderItemId.generate(), 3));
        repository.save(launched(orderId, SourceOrderItemId.generate(), 5));
        ProductionOrderViewService service = new ProductionOrderViewService(repository);

        OrderProductionView view = service.getOrderProductionView(orderId);

        assertEquals(OrderProductionViewStatus.IN_PRODUCTION, view.status());
        assertEquals(2, view.itemCount());
        assertEquals(2, view.inProductionCount());
    }

    @Test
    void doesNotIncludeOtherOrders() {
        SourceOrderId orderA = SourceOrderId.generate();
        SourceOrderId orderB = SourceOrderId.generate();
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(launched(orderA, SourceOrderItemId.generate(), 1));
        repository.save(launched(orderB, SourceOrderItemId.generate(), 1));
        ProductionOrderViewService service = new ProductionOrderViewService(repository);

        OrderProductionView view = service.getOrderProductionView(orderA);

        assertEquals(1, view.itemCount());
        assertTrue(repository.findBySourceOrderId(orderB).size() == 1);
    }

    private static ProductionItemState launched(
            SourceOrderId orderId, SourceOrderItemId itemId, long quantity) {
        return ProductionItemState.launch(
                ProductionFoundation.freeze(orderId, itemId, SpecificationId.generate(), T0),
                ProductionQuantity.positive(quantity),
                T0);
    }

    private static final class InMemoryRepository implements ProductionItemStateRepository {

        private final Map<String, ProductionItemState> store = new ConcurrentHashMap<>();

        @Override
        public ProductionItemState save(ProductionItemState state) {
            store.put(
                    state.sourceOrderId()
                            + ":"
                            + state.sourceOrderItemId()
                            + ":"
                            + state.specificationId(),
                    state);
            return state;
        }

        @Override
        public Optional<ProductionItemState> findByIdentity(
                SourceOrderId sourceOrderId,
                SourceOrderItemId sourceOrderItemId,
                SpecificationId specificationId) {
            return Optional.ofNullable(
                    store.get(sourceOrderId + ":" + sourceOrderItemId + ":" + specificationId));
        }

        @Override
        public List<ProductionItemState> findBySourceOrderId(SourceOrderId sourceOrderId) {
            return store.values().stream()
                    .filter(state -> state.sourceOrderId().equals(sourceOrderId))
                    .toList();
        }
    }
}
