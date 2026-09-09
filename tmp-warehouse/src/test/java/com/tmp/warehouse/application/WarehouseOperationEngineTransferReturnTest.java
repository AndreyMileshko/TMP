package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/**
 * Stage 3.5.8.3: in-memory unit coverage for {@link WarehouseOperationEngine#transferReturn}.
 */
class WarehouseOperationEngineTransferReturnTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);

    private InMemoryOperationRepository operations;
    private InMemoryStockPositionRepository stockPositions;
    private InMemoryMovementRepository movements;
    private WarehouseOperationEngine engine;

    private MaterialReference material;
    private WarehouseId warehouseId;
    private StorageCellId cellA1;
    private StorageCellId cellA5;

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
        material = MaterialReference.legacyArticle("RET-MAT");
        warehouseId = WarehouseId.generate();
        cellA1 = StorageCellId.generate();
        cellA5 = StorageCellId.generate();
    }

    @Test
    void transferReturnSameCellMovesInTransitToAvailable() {
        seedInTransit(cellA1, "25");

        WarehouseOperation completed =
                engine.transferReturn(
                        material,
                        warehouseId,
                        cellA1,
                        cellA1,
                        StockQuantity.of(new BigDecimal("10")));

        assertEquals(WarehouseOperationStatus.COMPLETED, completed.status());
        assertEquals(WarehouseOperationType.TRANSFER_RETURN, completed.type());
        assertEquals(
                0,
                qty(cellA1, StockState.IN_TRANSIT).compareTo(new BigDecimal("15")));
        assertEquals(
                0,
                qty(cellA1, StockState.AVAILABLE).compareTo(new BigDecimal("10")));
        assertTrue(
                movements.all.stream()
                        .allMatch(m -> m.operationType() == WarehouseOperationType.TRANSFER_RETURN));
        assertEquals(2, movements.all.size());
    }

    @Test
    void transferReturnToDifferentCellA1ToA5() {
        seedInTransit(cellA1, "40");

        WarehouseOperation completed =
                engine.transferReturn(
                        material,
                        warehouseId,
                        cellA1,
                        cellA5,
                        StockQuantity.of(new BigDecimal("40")));

        assertEquals(WarehouseOperationStatus.COMPLETED, completed.status());
        assertEquals(cellA5, completed.storageCellId());
        assertEquals(StockState.AVAILABLE, completed.stockState());
        assertEquals(
                0,
                qty(cellA1, StockState.IN_TRANSIT).compareTo(BigDecimal.ZERO));
        assertEquals(
                0,
                qty(cellA5, StockState.AVAILABLE).compareTo(new BigDecimal("40")));
        assertEquals(
                0,
                qty(cellA1, StockState.AVAILABLE).compareTo(BigDecimal.ZERO));
    }

    private void seedInTransit(StorageCellId cellId, String quantity) {
        stockPositions.create(
                StockPosition.of(
                        warehouseId,
                        cellId,
                        material,
                        StockState.IN_TRANSIT,
                        StockQuantity.of(new BigDecimal(quantity))));
    }

    private BigDecimal qty(StorageCellId cellId, StockState state) {
        return stockPositions
                .findByNaturalKey(warehouseId, cellId, material, state)
                .map(p -> p.quantity().value())
                .orElse(BigDecimal.ZERO);
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
        public List<WarehouseOperation> findByTypeAndStatus(
                WarehouseOperationType type, WarehouseOperationStatus status) {
            return store.values().stream()
                    .filter(op -> op.type() == type && op.status() == status)
                    .toList();
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

        @Override
        public StockPosition create(StockPosition position) {
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
        public List<StockPosition> findByMaterial(MaterialReference material) {
            return byId.values().stream().filter(p -> p.material().equals(material)).toList();
        }

        @Override
        public List<StockPosition> findByArticle(String article) {
            return byId.values().stream()
                    .filter(p -> p.material().article().equals(article))
                    .toList();
        }

        @Override
        public List<StockPosition> findByWarehouse(WarehouseId warehouseId) {
            return byId.values().stream()
                    .filter(p -> p.warehouseId().equals(warehouseId))
                    .toList();
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
            StockPosition current = byId.get(id);
            if (current == null) {
                throw new InvalidWarehouseStateException("missing stock " + id);
            }
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
