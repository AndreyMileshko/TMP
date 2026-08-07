package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockPositionId;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseMovement;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.WarehouseOperationStatus;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.domain.repository.WarehouseMovementRepository;
import com.tmp.warehouse.domain.repository.WarehouseOperationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class WarehouseOperationEngineTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-07T12:00:00Z"), ZoneOffset.UTC);

    private InMemoryOperationRepository operations;
    private InMemoryStockPositionRepository stockPositions;
    private InMemoryMovementRepository movements;
    private WarehouseOperationEngine engine;

    @BeforeEach
    void setUp() {
        operations = new InMemoryOperationRepository();
        stockPositions = new InMemoryStockPositionRepository();
        movements = new InMemoryMovementRepository();
        engine =
                new WarehouseOperationEngine(
                        operations,
                        stockPositions,
                        movements,
                        new TransactionTemplate(new PassthroughTransactionManager()),
                        CLOCK);
    }

    @Test
    void createPersistsDraftOperation() {
        WarehouseOperation created = createSampleDraft();
        assertEquals(WarehouseOperationStatus.DRAFT, created.status());
        assertTrue(engine.canExecute(created.id()));
        assertEquals(created.id(), engine.findById(created.id()).orElseThrow().id());
    }

    @Test
    void executeSuccessfullyCreatesMovementAndUpdatesStock() {
        WarehouseOperation draft = createSampleDraft();
        WarehouseOperation completed = engine.execute(draft.id());

        assertEquals(WarehouseOperationStatus.COMPLETED, completed.status());
        assertFalse(engine.canExecute(draft.id()));

        StockPosition position =
                stockPositions
                        .findByNaturalKey(
                                draft.warehouseId(),
                                draft.storageCellId(),
                                draft.material(),
                                draft.stockState())
                        .orElseThrow();
        assertEquals(StockQuantity.of(10), position.quantity());
        assertEquals(1, movements.findHistoryByStockPosition(position.id()).size());
        assertEquals(
                0,
                movements
                        .findHistoryByStockPosition(position.id())
                        .get(0)
                        .quantityDelta()
                        .compareTo(BigDecimal.TEN));
    }

    @Test
    void executeFailureMarksOperationFailedWithoutStockChange() {
        WarehouseOperation draft = createSampleDraft();
        stockPositions.failNextWrite = true;

        assertThrows(InvalidWarehouseStateException.class, () -> engine.execute(draft.id()));

        assertEquals(
                WarehouseOperationStatus.FAILED,
                engine.findById(draft.id()).orElseThrow().status());
        assertTrue(
                stockPositions
                        .findByNaturalKey(
                                draft.warehouseId(),
                                draft.storageCellId(),
                                draft.material(),
                                draft.stockState())
                        .isEmpty());
        assertTrue(movements.all.isEmpty());
    }

    @Test
    void completedOperationCannotBeExecutedAgain() {
        WarehouseOperation draft = createSampleDraft();
        engine.execute(draft.id());

        assertThrows(InvalidWarehouseStateException.class, () -> engine.execute(draft.id()));
        assertEquals(
                WarehouseOperationStatus.COMPLETED,
                engine.findById(draft.id()).orElseThrow().status());
        assertEquals(1, movements.all.size());
    }

    private WarehouseOperation createSampleDraft() {
        return engine.create(
                WarehouseOperationType.ADJUSTMENT,
                MaterialReference.of("VEKA-103.211"),
                WarehouseId.generate(),
                StorageCellId.generate(),
                StockState.AVAILABLE,
                StockQuantity.of(10));
    }

    private static final class PassthroughTransactionManager
            implements org.springframework.transaction.PlatformTransactionManager {

        @Override
        public org.springframework.transaction.TransactionStatus getTransaction(
                TransactionDefinition definition) throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(org.springframework.transaction.TransactionStatus status) {}

        @Override
        public void rollback(org.springframework.transaction.TransactionStatus status) {}
    }

    private static final class InMemoryOperationRepository implements WarehouseOperationRepository {
        private final Map<WarehouseOperationId, WarehouseOperation> store = new ConcurrentHashMap<>();

        @Override
        public WarehouseOperation create(WarehouseOperation operation) {
            store.put(operation.id(), operation);
            return operation;
        }

        @Override
        public Optional<WarehouseOperation> findById(WarehouseOperationId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public WarehouseOperation update(WarehouseOperation operation) {
            WarehouseOperation current = store.get(operation.id());
            WarehouseOperation persisted =
                    WarehouseOperation.rehydrate(
                            operation.id(),
                            operation.type(),
                            operation.status(),
                            operation.material(),
                            operation.warehouseId(),
                            operation.storageCellId(),
                            operation.stockState(),
                            operation.quantity(),
                            current.version() + 1);
            store.put(operation.id(), persisted);
            return persisted;
        }
    }

    private static final class InMemoryStockPositionRepository implements StockPositionRepository {
        private final Map<StockPositionId, StockPosition> byId = new ConcurrentHashMap<>();
        private boolean failNextWrite;

        @Override
        public StockPosition create(StockPosition position) {
            if (failNextWrite) {
                failNextWrite = false;
                throw new InvalidWarehouseStateException("simulated stock write failure");
            }
            byId.put(position.id(), position);
            return position;
        }

        @Override
        public Optional<StockPosition> findById(StockPositionId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<StockPosition> findByNaturalKey(
                WarehouseId warehouseId,
                StorageCellId storageCellId,
                MaterialReference material,
                StockState stockState) {
            return byId.values().stream()
                    .filter(
                            p ->
                                    p.warehouseId().equals(warehouseId)
                                            && p.storageCellId().equals(storageCellId)
                                            && p.material().equals(material)
                                            && p.stockState().equals(stockState))
                    .findFirst();
        }

        @Override
        public StockPosition updateQuantity(
                StockPositionId id, StockQuantity quantity, long expectedVersion) {
            return updateQuantityAndState(id, quantity, byId.get(id).stockState(), expectedVersion);
        }

        @Override
        public StockPosition updateState(
                StockPositionId id, StockState stockState, long expectedVersion) {
            return updateQuantityAndState(id, byId.get(id).quantity(), stockState, expectedVersion);
        }

        @Override
        public StockPosition updateQuantityAndState(
                StockPositionId id,
                StockQuantity quantity,
                StockState stockState,
                long expectedVersion) {
            if (failNextWrite) {
                failNextWrite = false;
                throw new InvalidWarehouseStateException("simulated stock write failure");
            }
            StockPosition current = byId.get(id);
            StockPosition updated =
                    StockPosition.rehydrate(
                            id,
                            current.warehouseId(),
                            current.storageCellId(),
                            current.material(),
                            stockState,
                            quantity,
                            expectedVersion + 1);
            byId.put(id, updated);
            return updated;
        }
    }

    private static final class InMemoryMovementRepository implements WarehouseMovementRepository {
        private final List<WarehouseMovement> all = new ArrayList<>();

        @Override
        public WarehouseMovement append(WarehouseMovement movement) {
            all.add(movement);
            return movement;
        }

        @Override
        public List<WarehouseMovement> findHistoryByStockPosition(StockPositionId stockPositionId) {
            return all.stream()
                    .filter(m -> m.stockPositionId().equals(stockPositionId))
                    .toList();
        }
    }
}
