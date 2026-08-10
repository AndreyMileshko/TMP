package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.warehouse.api.WarehouseApi.CreateReservationLinkCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.ReservationTargetTypeView;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.MaterialReservationLink;
import com.tmp.warehouse.domain.MaterialReservationLinkId;
import com.tmp.warehouse.domain.ReservationTargetReference;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockPositionId;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseMovement;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.repository.MaterialReservationLinkRepository;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.domain.repository.WarehouseMovementRepository;
import com.tmp.warehouse.domain.repository.WarehouseOperationRepository;
import com.tmp.warehouse.security.WarehousePermissions;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Authorization guards on Public API and Inventory (Specification §18).
 */
class WarehouseSecurityAuthorizationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-09T17:00:00Z"), ZoneOffset.UTC);

    private InMemoryStockPositionRepository stockPositions;
    private WarehouseOperationEngine engine;
    private WarehouseReservationLinkService reservationLinks;
    private WarehouseReceiptService receipts;
    private WarehouseMoveService moves;
    private WarehouseTransferService transfers;
    private WarehouseConsumptionService consumptions;
    private WarehouseAdjustmentService adjustments;

    @BeforeEach
    void setUp() {
        stockPositions = new InMemoryStockPositionRepository();
        InMemoryOperationRepository operations = new InMemoryOperationRepository();
        InMemoryMovementRepository movements = new InMemoryMovementRepository();
        engine =
                new WarehouseOperationEngine(
                        operations,
                        stockPositions,
                        movements,
                        new TransactionTemplate(new PassthroughTransactionManager()),
                        CLOCK);
        reservationLinks =
                new WarehouseReservationLinkService(new InMemoryReservationLinkRepository(), CLOCK);
        receipts = new WarehouseReceiptService(engine, stockPositions);
        moves = new WarehouseMoveService(engine);
        transfers = new WarehouseTransferService(engine);
        consumptions = new WarehouseConsumptionService(engine, stockPositions);
        adjustments = new WarehouseAdjustmentService(engine, stockPositions);
    }

    @Test
    void viewOperationsRequireWarehouseView() {
        DefaultWarehouseApi denied = api(Set.of());
        DefaultWarehouseApi allowed = api(Set.of(WarehousePermissions.WAREHOUSE_VIEW));

        assertThrows(AccessDeniedException.class, () -> denied.getStock("ALU-6060"));
        assertThrows(
                AccessDeniedException.class,
                () -> denied.checkAvailability("ALU-6060", BigDecimal.ONE));
        assertThrows(AccessDeniedException.class, () -> denied.listWarehouses());
        assertThrows(
                AccessDeniedException.class,
                () -> denied.getStockByWarehouse(UUID.randomUUID()));

        assertDoesNotThrow(() -> allowed.getStock("ALU-6060"));
        assertDoesNotThrow(() -> allowed.checkAvailability("ALU-6060", BigDecimal.ONE));
        assertDoesNotThrow(() -> allowed.listWarehouses());
        assertDoesNotThrow(() -> allowed.getStockByWarehouse(UUID.randomUUID()));
    }

    @Test
    void listWarehousesAllowedWithStructureViewWithoutStockView() {
        DefaultWarehouseApi api = api(Set.of(WarehousePermissions.WAREHOUSE_STRUCTURE_VIEW));
        assertDoesNotThrow(api::listWarehouses);
    }

    @Test
    void createWarehouseRequiresStructureCreatePermission() {
        DefaultWarehouseApi stockOnly = api(Set.of(WarehousePermissions.WAREHOUSE_VIEW));
        DefaultWarehouseApi createAllowed =
                api(Set.of(WarehousePermissions.WAREHOUSE_STRUCTURE_CREATE));

        assertThrows(
                AccessDeniedException.class,
                () ->
                        stockOnly.createWarehouse(
                                new com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand(
                                        "WH-1", "Main", true)));
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        createAllowed.createWarehouse(
                                new com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand(
                                        "WH-1", "Main", true)));
    }

    @Test
    void createStorageCellRequiresStructureCreatePermission() {
        DefaultWarehouseApi stockOnly = api(Set.of(WarehousePermissions.WAREHOUSE_VIEW));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        stockOnly.createStorageCell(
                                new com.tmp.warehouse.api.WarehouseApi.CreateStorageCellCommand(
                                        UUID.randomUUID(), "A-01", true)));
    }

    @Test
    void receiptIsDeniedWithoutReceiptPermission() {
        DefaultWarehouseApi api = api(Set.of(WarehousePermissions.WAREHOUSE_VIEW));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.executeWarehouseOperation(
                                ExecuteOperationCommand.receipt(
                                        "ALU-6060",
                                        BigDecimal.TEN,
                                        UUID.randomUUID(),
                                        UUID.randomUUID())));
    }

    @Test
    void receiptSucceedsWithReceiptPermission() {
        DefaultWarehouseApi api = api(Set.of(WarehousePermissions.WAREHOUSE_RECEIPT));
        assertDoesNotThrow(
                () ->
                        api.executeWarehouseOperation(
                                ExecuteOperationCommand.receipt(
                                        "ALU-6060",
                                        BigDecimal.TEN,
                                        UUID.randomUUID(),
                                        UUID.randomUUID())));
    }

    @Test
    void moveTransferConsumptionAdjustmentReservationAreDeniedWithoutMatchingPermission() {
        DefaultWarehouseApi api = api(Set.of(WarehousePermissions.WAREHOUSE_VIEW));
        UUID warehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        UUID destWarehouse = UUID.randomUUID();
        UUID destCell = UUID.randomUUID();

        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.executeWarehouseOperation(
                                ExecuteOperationCommand.move(
                                        "ALU-6060",
                                        BigDecimal.ONE,
                                        warehouseId,
                                        cellId,
                                        destWarehouse,
                                        destCell)));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.executeWarehouseOperation(
                                ExecuteOperationCommand.transferSend(
                                        "ALU-6060",
                                        BigDecimal.ONE,
                                        warehouseId,
                                        cellId,
                                        destWarehouse)));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.executeWarehouseOperation(
                                ExecuteOperationCommand.consumption(
                                        "ALU-6060", BigDecimal.ONE, warehouseId, cellId)));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.executeWarehouseOperation(
                                ExecuteOperationCommand.adjustment(
                                        "ALU-6060", BigDecimal.ONE, warehouseId, cellId)));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.createReservationLink(
                                new CreateReservationLinkCommand(
                                        "ALU-6060",
                                        ReservationTargetTypeView.ORDER,
                                        "26096190",
                                        BigDecimal.TEN)));
        assertThrows(
                AccessDeniedException.class, () -> api.listReservationLinks("ALU-6060"));
    }

    @Test
    void reservationSucceedsWithReservationPermission() {
        DefaultWarehouseApi api = api(Set.of(WarehousePermissions.WAREHOUSE_RESERVATION));
        assertDoesNotThrow(
                () ->
                        api.createReservationLink(
                                new CreateReservationLinkCommand(
                                        "ALU-6060",
                                        ReservationTargetTypeView.ORDER,
                                        "26096190",
                                        BigDecimal.TEN)));
    }

    @Test
    void inventoryIsDeniedWithoutInventoryPermission() {
        WarehouseInventoryService inventory =
                new WarehouseInventoryService(
                        new FixedAuthorization(Set.of()), adjustments, stockPositions);
        assertThrows(
                AccessDeniedException.class,
                () ->
                        inventory.reconcile(
                                new InventoryCountRequest(
                                        MaterialReference.of("ALU-6060"),
                                        StockQuantity.of(1L),
                                        WarehouseId.generate(),
                                        StorageCellId.generate())));
    }

    @Test
    void inventorySucceedsWithInventoryPermissionWhenCountMatchesEmptyStock() {
        WarehouseInventoryService inventory =
                new WarehouseInventoryService(
                        new FixedAuthorization(Set.of(WarehousePermissions.WAREHOUSE_INVENTORY)),
                        adjustments,
                        stockPositions);
        assertDoesNotThrow(
                () ->
                        inventory.reconcile(
                                new InventoryCountRequest(
                                        MaterialReference.of("ALU-6060"),
                                        StockQuantity.of(0L),
                                        WarehouseId.generate(),
                                        StorageCellId.generate())));
    }

    private DefaultWarehouseApi api(Set<PermissionId> granted) {
        return new DefaultWarehouseApi(
                new FixedAuthorization(granted),
                new EmptyWarehouseCatalog(),
                stockPositions,
                reservationLinks,
                receipts,
                moves,
                transfers,
                consumptions,
                adjustments);
    }

    private static final class EmptyWarehouseCatalog
            implements com.tmp.warehouse.domain.repository.WarehouseCatalogRepository {
        @Override
        public java.util.List<com.tmp.warehouse.domain.Warehouse> findAll() {
            return java.util.List.of();
        }

        @Override
        public com.tmp.warehouse.domain.Warehouse save(com.tmp.warehouse.domain.Warehouse warehouse) {
            throw new UnsupportedOperationException("not used in authorization test");
        }

        @Override
        public com.tmp.warehouse.domain.StorageCell save(com.tmp.warehouse.domain.StorageCell cell) {
            throw new UnsupportedOperationException("not used in authorization test");
        }

        @Override
        public java.util.List<com.tmp.warehouse.domain.StorageCell> findStorageCellsByWarehouse(
                WarehouseId warehouseId) {
            return java.util.List.of();
        }
    }

    private static final class FixedAuthorization implements AuthorizationService {
        private final Set<PermissionId> allowed;

        private FixedAuthorization(Set<PermissionId> allowed) {
            this.allowed = Set.copyOf(allowed);
        }

        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return allowed.contains(permissionId);
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
            if (!hasPermission(permissionId)) {
                throw new AccessDeniedException(
                        "Access denied for permission: " + permissionId.value());
            }
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return allowed;
        }
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

    private static final class InMemoryReservationLinkRepository
            implements MaterialReservationLinkRepository {
        private final Map<MaterialReservationLinkId, MaterialReservationLink> byId =
                new ConcurrentHashMap<>();

        @Override
        public MaterialReservationLink create(MaterialReservationLink link) {
            byId.put(link.id(), link);
            return link;
        }

        @Override
        public Optional<MaterialReservationLink> findById(MaterialReservationLinkId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<MaterialReservationLink> findByMaterial(MaterialReference material) {
            return byId.values().stream().filter(l -> l.material().equals(material)).toList();
        }

        @Override
        public List<MaterialReservationLink> findByTarget(ReservationTargetReference target) {
            return byId.values().stream().filter(l -> l.target().equals(target)).toList();
        }
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
