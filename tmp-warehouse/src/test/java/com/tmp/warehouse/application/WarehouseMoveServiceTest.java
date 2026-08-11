package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class WarehouseMoveServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-07T16:00:00Z"), ZoneOffset.UTC);

    private InMemoryOperationRepository operations;
    private InMemoryStockPositionRepository stockPositions;
    private InMemoryMovementRepository movements;
    private WarehouseMoveService moves;

    @BeforeEach
    void setUp() {
        operations = new InMemoryOperationRepository();
        stockPositions = new InMemoryStockPositionRepository();
        movements = new InMemoryMovementRepository();
        WarehouseOperationEngine engine =
                new WarehouseOperationEngine(
                        operations,
                        stockPositions,
                        movements,
                        new TransactionTemplate(new PassthroughTransactionManager()),
                        CLOCK);
        moves = new WarehouseMoveService(engine);
    }

    @Test
    void successfulMoveRelocatesStockWithoutChangingQuantityOrMaterial() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId sourceCell = StorageCellId.generate();
        StorageCellId destinationCell = StorageCellId.generate();
        MaterialReference material = MaterialReference.legacyArticle("VEKA 103.211 WHITE");
        stockPositions.create(
                StockPosition.of(
                        warehouseId,
                        sourceCell,
                        material,
                        StockState.AVAILABLE,
                        StockQuantity.of(500L)));

        WarehouseOperation completed =
                moves.move(
                        new MoveRequest(
                                material,
                                StockQuantity.of(500L),
                                warehouseId,
                                sourceCell,
                                warehouseId,
                                destinationCell));

        assertEquals(WarehouseOperationType.MOVE, completed.type());
        assertEquals(WarehouseOperationStatus.COMPLETED, completed.status());
        assertEquals(sourceCell, completed.storageCellId());
        assertEquals(StockQuantity.of(500L), completed.quantity());

        StockPosition source =
                stockPositions
                        .findByNaturalKey(warehouseId, sourceCell, material, StockState.AVAILABLE)
                        .orElseThrow();
        StockPosition destination =
                stockPositions
                        .findByNaturalKey(
                                warehouseId, destinationCell, material, StockState.AVAILABLE)
                        .orElseThrow();
        assertEquals(StockQuantity.zero(), source.quantity());
        assertEquals(StockQuantity.of(500L), destination.quantity());
        assertEquals(material, destination.material());
        assertEquals(warehouseId, destination.warehouseId());

        List<WarehouseMovement> sourceHistory =
                movements.findHistoryByStockPosition(source.id());
        List<WarehouseMovement> destHistory =
                movements.findHistoryByStockPosition(destination.id());
        assertEquals(1, sourceHistory.size());
        assertEquals(1, destHistory.size());
        assertEquals(WarehouseOperationType.MOVE, sourceHistory.get(0).operationType());
        assertEquals(WarehouseOperationType.MOVE, destHistory.get(0).operationType());
        assertEquals(0, sourceHistory.get(0).quantityDelta().compareTo(BigDecimal.valueOf(-500L)));
        assertEquals(0, destHistory.get(0).quantityDelta().compareTo(BigDecimal.valueOf(500L)));
    }

    @Test
    void insufficientStockRejected() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId sourceCell = StorageCellId.generate();
        StorageCellId destinationCell = StorageCellId.generate();
        MaterialReference material = MaterialReference.legacyArticle("ALU-6060");
        stockPositions.create(
                StockPosition.of(
                        warehouseId,
                        sourceCell,
                        material,
                        StockState.AVAILABLE,
                        StockQuantity.of(10L)));

        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        moves.move(
                                new MoveRequest(
                                        material,
                                        StockQuantity.of(11L),
                                        warehouseId,
                                        sourceCell,
                                        warehouseId,
                                        destinationCell)));

        assertEquals(
                StockQuantity.of(10L),
                stockPositions
                        .findByNaturalKey(warehouseId, sourceCell, material, StockState.AVAILABLE)
                        .orElseThrow()
                        .quantity());
        assertTrue(
                stockPositions
                        .findByNaturalKey(
                                warehouseId, destinationCell, material, StockState.AVAILABLE)
                        .isEmpty());
        assertTrue(movements.all.isEmpty());
    }

    @Test
    void differentWarehousesRejected() {
        WarehouseId sourceWarehouse = WarehouseId.generate();
        WarehouseId destinationWarehouse = WarehouseId.generate();
        StorageCellId sourceCell = StorageCellId.generate();
        StorageCellId destinationCell = StorageCellId.generate();
        MaterialReference material = MaterialReference.legacyArticle("ALU-6060");
        stockPositions.create(
                StockPosition.of(
                        sourceWarehouse,
                        sourceCell,
                        material,
                        StockState.AVAILABLE,
                        StockQuantity.of(50L)));

        assertThrows(
                InvalidWarehouseStateException.class,
                () ->
                        moves.move(
                                new MoveRequest(
                                        material,
                                        StockQuantity.of(10L),
                                        sourceWarehouse,
                                        sourceCell,
                                        destinationWarehouse,
                                        destinationCell)));

        assertEquals(
                StockQuantity.of(50L),
                stockPositions
                        .findByNaturalKey(
                                sourceWarehouse, sourceCell, material, StockState.AVAILABLE)
                        .orElseThrow()
                        .quantity());
        assertTrue(movements.all.isEmpty());
    }

    @Test
    void moveAddsToExistingDestinationPosition() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId sourceCell = StorageCellId.generate();
        StorageCellId destinationCell = StorageCellId.generate();
        MaterialReference material = MaterialReference.legacyArticle("VEKA-103.211");
        stockPositions.create(
                StockPosition.of(
                        warehouseId,
                        sourceCell,
                        material,
                        StockState.AVAILABLE,
                        StockQuantity.of(100L)));
        stockPositions.create(
                StockPosition.of(
                        warehouseId,
                        destinationCell,
                        material,
                        StockState.AVAILABLE,
                        StockQuantity.of(20L)));

        moves.move(
                new MoveRequest(
                        material,
                        StockQuantity.of(30L),
                        warehouseId,
                        sourceCell,
                        warehouseId,
                        destinationCell));

        assertEquals(
                StockQuantity.of(70L),
                stockPositions
                        .findByNaturalKey(warehouseId, sourceCell, material, StockState.AVAILABLE)
                        .orElseThrow()
                        .quantity());
        assertEquals(
                StockQuantity.of(50L),
                stockPositions
                        .findByNaturalKey(
                                warehouseId, destinationCell, material, StockState.AVAILABLE)
                        .orElseThrow()
                        .quantity());
    }

    @Test
    void moveRejectsNonPositiveQuantity() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MoveRequest(
                                MaterialReference.legacyArticle("MAT-1"),
                                StockQuantity.of(0),
                                WarehouseId.generate(),
                                StorageCellId.generate(),
                                WarehouseId.generate(),
                                StorageCellId.generate()));
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
            return byId.values().stream()
                    .filter(p -> p.material().equals(material))
                    .toList();
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
