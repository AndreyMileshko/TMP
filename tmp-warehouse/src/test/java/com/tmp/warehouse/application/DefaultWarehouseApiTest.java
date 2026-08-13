package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.AvailabilityStatus;
import com.tmp.warehouse.api.WarehouseApi.CreateReservationLinkCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.OperationKind;
import com.tmp.warehouse.api.WarehouseApi.ReservationLinkView;
import com.tmp.warehouse.api.WarehouseApi.ReservationTargetTypeView;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceDisplayView;
import com.tmp.warehouse.api.WarehouseApi.StockStateView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.warehouse.application.FixedMaterialReferenceDisplayPort;
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
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.MaterialReservationLinkRepository;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.tmp.warehouse.testsupport.InMemoryMaterialReferenceRepository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit tests: Public API mapping and orchestration without leaking domain types.
 */
class DefaultWarehouseApiTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-09T16:00:00Z"), ZoneOffset.UTC);

    private InMemoryMaterialReferenceRepository materials;
    private InMemoryStockPositionRepository stockPositions;
    private InMemoryReservationLinkRepository links;
    private DefaultWarehouseApi api;

    @BeforeEach
    void setUp() {
        materials = new InMemoryMaterialReferenceRepository();
        stockPositions = new InMemoryStockPositionRepository();
        links = new InMemoryReservationLinkRepository();
        InMemoryOperationRepository operations = new InMemoryOperationRepository();
        InMemoryMovementRepository movements = new InMemoryMovementRepository();
        WarehouseOperationEngine engine =
                new WarehouseOperationEngine(
                        operations,
                        stockPositions,
                        movements,
                        new TransactionTemplate(new PassthroughTransactionManager()),
                        CLOCK);
        api =
                new DefaultWarehouseApi(
                        AllowingAuthorization.INSTANCE,
                        new EmptyWarehouseCatalog(),
                        stockPositions,
                        materials,
                        new FixedMaterialReferenceDisplayPort()
                                .register(
                                        "VEKA 103.211",
                                        "Профиль VEKA Softline",
                                        "Белый",
                                        "6000 мм",
                                        "шт."),
                        new WarehouseReservationLinkService(links, CLOCK),
                        new WarehouseReceiptService(engine, stockPositions, materials),
                        new WarehouseMoveService(engine),
                        new WarehouseTransferService(engine),
                        new WarehouseConsumptionService(engine, stockPositions),
                        new WarehouseAdjustmentService(engine, stockPositions));
    }

    private static final class EmptyWarehouseCatalog
            implements com.tmp.warehouse.domain.repository.WarehouseCatalogRepository {
        @Override
        public List<com.tmp.warehouse.domain.Warehouse> findAll() {
            return List.of();
        }

        @Override
        public com.tmp.warehouse.domain.Warehouse save(com.tmp.warehouse.domain.Warehouse warehouse) {
            throw new UnsupportedOperationException("not used in unit test");
        }

        @Override
        public com.tmp.warehouse.domain.StorageCell save(com.tmp.warehouse.domain.StorageCell cell) {
            throw new UnsupportedOperationException("not used in unit test");
        }

        @Override
        public List<com.tmp.warehouse.domain.StorageCell> findStorageCellsByWarehouse(
                WarehouseId warehouseId) {
            return List.of();
        }
    }

    private enum AllowingAuthorization implements AuthorizationService {
        INSTANCE;

        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return true;
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
            // allow all for Public API mapping unit tests
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of();
        }
    }

    @Test
    void getMaterialReferenceDisplayMapsPortFields() {
        MaterialReferenceDisplayView view = api.getMaterialReferenceDisplay("VEKA 103.211");
        assertEquals("VEKA 103.211", view.article());
        assertEquals("Профиль VEKA Softline", view.materialName());
        assertEquals("Белый", view.color());
        assertEquals("6000 мм", view.size());
        assertEquals("шт.", view.unitOfMeasure());
    }

    @Test
    void getStockMapsDomainPositionsToPublicViews() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellId = StorageCellId.generate();
        MaterialReference material =
                materials.create(
                        MaterialReference.create(
                                "VEKA 103.211",
                                "Профиль VEKA Softline",
                                "Белый",
                                "6000 мм",
                                "шт."));
        stockPositions.create(
                StockPosition.of(
                        warehouseId, cellId, material, StockState.AVAILABLE, StockQuantity.of(25L)));

        List<StockView> views = api.getStock("VEKA 103.211");

        assertEquals(1, views.size());
        StockView view = views.get(0);
        assertEquals("VEKA 103.211", view.materialCode());
        assertEquals("VEKA 103.211", view.article());
        assertEquals("Профиль VEKA Softline", view.materialName());
        assertEquals("Белый", view.color());
        assertEquals("6000 мм", view.size());
        assertEquals("шт.", view.unitOfMeasure());
        assertEquals(warehouseId.value(), view.warehouseId());
        assertEquals(cellId.value(), view.storageCellId());
        assertEquals(0, view.quantity().compareTo(BigDecimal.valueOf(25L)));
        assertEquals(StockStateView.AVAILABLE, view.stockState());
    }

    @Test
    void getStockFiltersByWarehouseAndCell() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellA = StorageCellId.generate();
        StorageCellId cellB = StorageCellId.generate();
        MaterialReference material = materials.create(MaterialReference.legacyArticle("ALU-6060"));
        stockPositions.create(
                StockPosition.of(
                        warehouseId, cellA, material, StockState.AVAILABLE, StockQuantity.of(10L)));
        stockPositions.create(
                StockPosition.of(
                        warehouseId, cellB, material, StockState.AVAILABLE, StockQuantity.of(5L)));

        List<StockView> filtered = api.getStock("ALU-6060", warehouseId.value(), cellA.value());

        assertEquals(1, filtered.size());
        assertEquals(cellA.value(), filtered.get(0).storageCellId());
    }

    @Test
    void checkAvailabilitySumsAvailableStockOnly() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellA = StorageCellId.generate();
        StorageCellId cellB = StorageCellId.generate();
        MaterialReference material = materials.create(MaterialReference.legacyArticle("ALU-6060"));
        stockPositions.create(
                StockPosition.of(
                        warehouseId, cellA, material, StockState.AVAILABLE, StockQuantity.of(40L)));
        stockPositions.create(
                StockPosition.of(
                        warehouseId, cellB, material, StockState.BLOCKED, StockQuantity.of(100L)));

        WarehouseApi.AvailabilityResult ok = api.checkAvailability("ALU-6060", BigDecimal.valueOf(30));
        assertEquals(AvailabilityStatus.AVAILABLE, ok.status());
        assertTrue(ok.isAvailable());
        assertEquals(0, ok.availableQuantity().compareTo(BigDecimal.valueOf(40L)));

        WarehouseApi.AvailabilityResult shortfall =
                api.checkAvailability("ALU-6060", BigDecimal.valueOf(50));
        assertEquals(AvailabilityStatus.INSUFFICIENT, shortfall.status());
        assertFalse(shortfall.isAvailable());
    }

    @Test
    void createReservationLinkReturnsPublicViewWithoutStockMutation() {
        MaterialReference material = materials.create(MaterialReference.legacyArticle("VEKA 103.211"));
        ReservationLinkView view =
                api.createReservationLink(
                        new CreateReservationLinkCommand(
                                material.id().value(),
                                ReservationTargetTypeView.ORDER,
                                "26096190",
                                BigDecimal.valueOf(200)));

        assertEquals("VEKA 103.211", view.materialCode());
        assertEquals(ReservationTargetTypeView.ORDER, view.targetType());
        assertEquals("26096190", view.targetReference());
        assertEquals(0, view.quantity().compareTo(BigDecimal.valueOf(200)));
        assertTrue(api.getStock("VEKA 103.211").isEmpty());
        assertEquals(1, links.size());
    }

    @Test
    void executeReceiptReturnsCompletedOperationResult() {
        WarehouseId warehouseId = WarehouseId.generate();
        StorageCellId cellId = StorageCellId.generate();

        WarehouseApi.OperationResult result =
                api.executeWarehouseOperation(
                        ExecuteOperationCommand.receipt(
                                "ALU-6060",
                                "ALU-6060",
                                "",
                                "",
                                "шт.",
                                BigDecimal.TEN,
                                warehouseId.value(),
                                cellId.value()));

        assertEquals(OperationKind.RECEIPT, result.kind());
        assertEquals("COMPLETED", result.status());
        assertEquals("ALU-6060", result.materialCode());
        assertEquals(1, api.getStock("ALU-6060").size());
    }

    @Test
    void executeMoveRequiresDestinationFields() {
        UUID warehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        UUID materialReferenceId = UUID.randomUUID();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        api.executeWarehouseOperation(
                                new ExecuteOperationCommand(
                                        OperationKind.MOVE,
                                        null,
                                        BigDecimal.ONE,
                                        warehouseId,
                                        cellId,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        materialReferenceId)));
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

    private static final class InMemoryReservationLinkRepository
            implements MaterialReservationLinkRepository {
        private final Map<MaterialReservationLinkId, MaterialReservationLink> byId =
                new ConcurrentHashMap<>();

        int size() {
            return byId.size();
        }

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
