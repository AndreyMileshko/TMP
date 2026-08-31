package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.AvailabilityResult;
import com.tmp.warehouse.api.WarehouseApi.AvailabilityStatus;
import com.tmp.warehouse.api.WarehouseApi.CreateReservationLinkCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateStorageCellCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.OperationKind;
import com.tmp.warehouse.api.WarehouseApi.OperationResult;
import com.tmp.warehouse.api.WarehouseApi.ReservationLinkView;
import com.tmp.warehouse.api.WarehouseApi.ReservationTargetTypeView;
import com.tmp.warehouse.api.WarehouseApi.StockStateView;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceDisplayView;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.TransferRequestView;
import com.tmp.warehouse.api.WarehouseApi.TransferStatusView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WarehouseWorkbenchViewModelTest {

    private FakeWarehouseApi api;
    private FakeAuthorization auth;
    private WarehouseWorkbenchViewModel viewModel;

    @BeforeEach
    void setUp() {
        api = new FakeWarehouseApi();
        auth = new FakeAuthorization(Set.of(
                UiShellScreens.WAREHOUSE_VIEW_PERMISSION,
                UiShellScreens.WAREHOUSE_RECEIPT_PERMISSION,
                UiShellScreens.WAREHOUSE_MOVE_PERMISSION,
                UiShellScreens.WAREHOUSE_TRANSFER_PERMISSION,
                UiShellScreens.WAREHOUSE_CONSUMPTION_PERMISSION,
                UiShellScreens.WAREHOUSE_ADJUSTMENT_PERMISSION,
                UiShellScreens.WAREHOUSE_RESERVATION_PERMISSION,
                UiShellScreens.WAREHOUSE_STRUCTURE_CREATE_PERMISSION,
                UiShellScreens.WAREHOUSE_STORAGE_CELL_CREATE_PERMISSION,
                UiShellScreens.WAREHOUSE_STRUCTURE_DELETE_PERMISSION,
                UiShellScreens.WAREHOUSE_STORAGE_CELL_DELETE_PERMISSION));
        viewModel = new WarehouseWorkbenchViewModel(api, auth);
    }

    @Test
    void loadsWarehousesThroughPublicApiWhenViewAllowed() {
        UUID id = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(id, "WH-1", "Main", true));
        viewModel.loadWarehouses();
        assertEquals(1, viewModel.warehouses().size());
        assertEquals("WH-1", viewModel.warehouses().get(0).code());
        assertEquals(1, api.listWarehousesCalls);
        assertTrue(viewModel.errorMessageProperty().get().isBlank());
    }

    @Test
    void createsWarehouseThroughPublicApi() {
        viewModel.newWarehouseCodeProperty().set("WH-NEW");
        viewModel.newWarehouseNameProperty().set("Новый");
        viewModel.newWarehouseActiveProperty().set(true);
        viewModel.createWarehouse();
        assertEquals(1, api.createWarehouseCalls.size());
        assertEquals("WH-NEW", api.createWarehouseCalls.get(0).code());
        assertEquals(1, viewModel.warehouses().size());
        assertTrue(viewModel.statusMessageProperty().get().contains("Склад создан"));
    }

    @Test
    void operatorWithoutStructureCreateDoesNotCreateWarehouse() {
        auth.allowed =
                Set.of(
                        UiShellScreens.WAREHOUSE_VIEW_PERMISSION,
                        UiShellScreens.WAREHOUSE_RECEIPT_PERMISSION);
        viewModel.refreshPermissions();
        assertFalse(viewModel.canCreateWarehouseProperty().get());
        assertFalse(viewModel.canCreateStorageCellProperty().get());
        viewModel.newWarehouseCodeProperty().set("WH-X");
        viewModel.newWarehouseNameProperty().set("X");
        viewModel.createWarehouse();
        assertEquals(0, api.createWarehouseCalls.size());
        assertEquals(WarehouseUiErrorMapper.ACCESS_DENIED, viewModel.errorMessageProperty().get());
    }

    @Test
    void structureAdministratorSeesCreateCapabilities() {
        auth.allowed =
                Set.of(
                        UiShellScreens.WAREHOUSE_STRUCTURE_CREATE_PERMISSION,
                        UiShellScreens.WAREHOUSE_STORAGE_CELL_CREATE_PERMISSION,
                        UiShellScreens.WAREHOUSE_STRUCTURE_DELETE_PERMISSION,
                        UiShellScreens.WAREHOUSE_STORAGE_CELL_DELETE_PERMISSION);
        viewModel.refreshPermissions();
        assertTrue(viewModel.canCreateWarehouseProperty().get());
        assertTrue(viewModel.canCreateStorageCellProperty().get());
        assertTrue(viewModel.canDeleteWarehouseProperty().get());
        assertTrue(viewModel.canDeleteStorageCellProperty().get());
        assertFalse(viewModel.canViewProperty().get());
    }

    @Test
    void createsStorageCellThroughPublicApi() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        viewModel.loadWarehouses();
        viewModel.newCellWarehouseProperty().set(WarehouseChoice.from(api.warehouses.get(0)));
        viewModel.newCellCodeProperty().set("A-01");
        viewModel.newCellActiveProperty().set(true);
        viewModel.createStorageCell();
        assertEquals(1, api.createCellCalls.size());
        assertEquals("A-01", api.createCellCalls.get(0).code());
        assertEquals(1, viewModel.warehouseCells().size());
    }

    @Test
    void deniesWarehouseListWithoutViewCapability() {
        auth.allowed = Set.of();
        viewModel.refreshPermissions();
        viewModel.loadWarehouses();
        assertEquals(WarehouseUiErrorMapper.ACCESS_DENIED, viewModel.errorMessageProperty().get());
        assertEquals(0, api.listWarehousesCalls);
    }

    @Test
    void loadsStockByWarehouseThroughPublicApi() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.stockByWarehouse.put(
                warehouseId,
                List.of(
                        StockView.of(
                                UUID.randomUUID(),
                                "ALU-6060",
                                "Алюминий",
                                "Серый",
                                "6000 мм",
                                "шт",
                                "WH-1 — Main",
                                "A-01",
                                BigDecimal.TEN,
                                StockStateView.AVAILABLE,
                                warehouseId,
                                UUID.randomUUID())));
        viewModel.loadWarehouses();
        viewModel.stockWarehouseProperty().set(WarehouseChoice.from(api.warehouses.get(0)));
        viewModel.loadStock();
        assertEquals(1, viewModel.stockRows().size());
        assertEquals("ALU-6060", viewModel.stockRows().get(0).article());
        assertEquals("Алюминий", viewModel.stockRows().get(0).materialName());
        assertEquals(1, api.getStockByWarehouseCalls);
    }

    @Test
    void receiptCallsExecuteWarehouseOperationWithChoices() {
        UUID warehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        WarehouseView warehouse = new WarehouseView(warehouseId, "WH-1", "Main", true);
        StorageCellView cell = new StorageCellView(cellId, warehouseId, "A-01", true);
        api.warehouses.add(warehouse);
        api.cellsByWarehouse.put(warehouseId, new ArrayList<>(List.of(cell)));
        viewModel.loadWarehouses();
        viewModel.receiptArticleProperty().set("ALU-6060");
        viewModel.receiptNameProperty().set("ALU-6060");
        viewModel.receiptUnitProperty().set("шт.");
        viewModel.receiptQuantityProperty().set("12.5");
        viewModel.receiptWarehouseProperty().set(WarehouseChoice.from(warehouse));
        viewModel.receiptCellProperty().set(StorageCellChoice.from(cell));
        viewModel.submitReceipt();
        assertEquals(1, api.executeCalls.size());
        ExecuteOperationCommand command = api.executeCalls.get(0);
        assertEquals(OperationKind.RECEIPT, command.kind());
        assertEquals(warehouseId, command.warehouseId());
        assertEquals(cellId, command.storageCellId());
        assertTrue(viewModel.statusMessageProperty().get().contains("Поступление"));
    }

    @Test
    void moveUsesSameWarehouseForDestination() {
        UUID wh = UUID.randomUUID();
        UUID cell = UUID.randomUUID();
        UUID destCell = UUID.randomUUID();
        WarehouseView warehouse = new WarehouseView(wh, "WH-1", "Main", true);
        api.warehouses.add(warehouse);
        UUID materialId = UUID.randomUUID();
        api.materialReferences.add(
                new MaterialReferenceView(
                        materialId, "M1", "Material 1", "", "", ""));
        viewModel.loadWarehouses();
        viewModel.moveMaterialProperty().set(MaterialChoice.from(api.materialReferences.get(0)));
        viewModel.moveQuantityProperty().set("1");
        viewModel.moveSourceWarehouseProperty().set(WarehouseChoice.from(warehouse));
        viewModel.moveSourceCellProperty().set(new StorageCellChoice(cell, wh, "A-01", true));
        viewModel.moveDestCellProperty().set(new StorageCellChoice(destCell, wh, "B-01", true));
        viewModel.submitMove();
        assertEquals(1, api.executeCalls.size());
        assertEquals(OperationKind.MOVE, api.executeCalls.get(0).kind());
        assertEquals(wh, api.executeCalls.get(0).warehouseId());
        assertEquals(wh, api.executeCalls.get(0).destinationWarehouseId());
        assertEquals(destCell, api.executeCalls.get(0).destinationStorageCellId());
    }

    @Test
    void transferSendUsesExistingDraftIdThroughPublicApi() {
        UUID wh = UUID.randomUUID();
        UUID destWh = UUID.randomUUID();
        UUID cell = UUID.randomUUID();
        UUID destCell = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        WarehouseView source = new WarehouseView(wh, "WH-S", "Source", true);
        WarehouseView destination = new WarehouseView(destWh, "WH-D", "Dest", true);
        api.warehouses.add(source);
        api.warehouses.add(destination);
        MaterialChoice material = api.addMaterial("M1");
        api.transferDrafts.add(
                new TransferRequestView(
                        draftId,
                        "DRAFT",
                        material.id(),
                        new BigDecimal("2"),
                        wh,
                        cell,
                        destWh,
                        destCell));
        viewModel.loadWarehouses();
        viewModel.selectSection(WarehouseSection.TRANSFER);

        assertEquals(1, viewModel.transferDraftRows().size());
        viewModel.selectedTransferDraftProperty().set(viewModel.transferDraftRows().get(0));
        viewModel.submitTransferSend();

        assertEquals(List.of(draftId), api.sendTransferCalls);
        assertEquals(0, api.executeCalls.size());
    }

    @Test
    void transferReceiveStillUsesGenericPublicApi() {
        UUID wh = UUID.randomUUID();
        UUID destWh = UUID.randomUUID();
        UUID cell = UUID.randomUUID();
        UUID destCell = UUID.randomUUID();
        WarehouseView source = new WarehouseView(wh, "WH-S", "Source", true);
        WarehouseView destination = new WarehouseView(destWh, "WH-D", "Dest", true);
        api.warehouses.add(source);
        api.warehouses.add(destination);
        MaterialChoice material = api.addMaterial("M1");
        viewModel.loadWarehouses();

        viewModel.transferReceiveMaterialProperty().set(material);
        viewModel.transferReceiveQuantityProperty().set("2");
        viewModel.transferReceiveSourceWarehouseProperty().set(WarehouseChoice.from(source));
        viewModel.transferReceiveSourceCellProperty()
                .set(new StorageCellChoice(cell, wh, "A-01", true));
        viewModel.transferReceiveDestWarehouseProperty().set(WarehouseChoice.from(destination));
        viewModel.transferReceiveDestCellProperty()
                .set(new StorageCellChoice(destCell, destWh, "B-01", true));
        viewModel.submitTransferReceive();

        assertEquals(1, api.executeCalls.size());
        assertEquals(OperationKind.TRANSFER_RECEIVE, api.executeCalls.get(0).kind());
    }

    @Test
    void consumptionAndAdjustmentUseWarehouseAndCellChoices() {
        UUID wh = UUID.randomUUID();
        UUID cell = UUID.randomUUID();
        WarehouseView warehouse = new WarehouseView(wh, "WH-1", "Main", true);
        api.warehouses.add(warehouse);
        MaterialChoice material = api.addMaterial("M1");
        viewModel.loadWarehouses();

        viewModel.consumptionMaterialProperty().set(material);
        viewModel.consumptionQuantityProperty().set("3");
        viewModel.consumptionWarehouseProperty().set(WarehouseChoice.from(warehouse));
        viewModel.consumptionCellProperty().set(new StorageCellChoice(cell, wh, "A-01", true));
        viewModel.consumptionBasisProperty().set("Производство");
        viewModel.submitConsumption();

        viewModel.adjustmentMaterialProperty().set(material);
        viewModel.adjustmentQuantityDeltaProperty().set("-1");
        viewModel.adjustmentWarehouseProperty().set(WarehouseChoice.from(warehouse));
        viewModel.adjustmentCellProperty().set(new StorageCellChoice(cell, wh, "A-01", true));
        viewModel.adjustmentReasonProperty().set("Инвентаризация");
        viewModel.submitAdjustment();

        assertEquals(2, api.executeCalls.size());
        assertEquals(OperationKind.CONSUMPTION, api.executeCalls.get(0).kind());
        assertEquals(OperationKind.ADJUSTMENT, api.executeCalls.get(1).kind());
        assertTrue(viewModel.statusMessageProperty().get().contains("причина=Инвентаризация"));
    }

    @Test
    void reservationSectionUsesListAndCreateThroughPublicApi() {
        api.materialReferences.add(
                new MaterialReferenceView(
                        UUID.randomUUID(), "ALU-6060", "ALU-6060", "", "", ""));
        viewModel.reservationFilterMaterialProperty().set("ALU-6060");
        viewModel.loadReservationLinks();
        assertEquals(1, api.listReservationCalls);

        viewModel.reservationMaterialProperty().set("ALU-6060");
        viewModel.reservationOrderRefProperty().set("ORD-1");
        viewModel.reservationQuantityProperty().set("5");
        viewModel.submitReservationLink();
        assertEquals(1, api.createReservationCalls.size());
        assertEquals(1, viewModel.reservationLinks().size());
        assertEquals("ORD-1", viewModel.reservationLinks().get(0).targetReference());
    }

    @Test
    void sectionWithoutCapabilityIsDeniedAndDoesNotCallApi() {
        auth.allowed = Set.of(UiShellScreens.WAREHOUSE_VIEW_PERMISSION);
        viewModel.refreshPermissions();
        assertFalse(viewModel.canReceiptProperty().get());
        viewModel.selectSection(WarehouseSection.RECEIPT);
        assertEquals(WarehouseUiErrorMapper.ACCESS_DENIED, viewModel.errorMessageProperty().get());
        assertEquals(WarehouseSection.WAREHOUSES, viewModel.sectionProperty().get());
    }

    @Test
    void inventorySectionNavigatesToAdjustment() {
        viewModel.selectSection(WarehouseSection.INVENTORY);
        assertEquals(WarehouseSection.INVENTORY, viewModel.sectionProperty().get());
        viewModel.openAdjustmentFromInventory();
        assertEquals(WarehouseSection.ADJUSTMENT, viewModel.sectionProperty().get());
    }

    @Test
    void formatMaterialDisplayBuildsReadableDescription() {
        MaterialReferenceView view =
                new MaterialReferenceView(
                        UUID.randomUUID(),
                        "VEKA-103.211",
                        "Профиль VEKA Softline",
                        "Белый",
                        "6000 мм",
                        "шт");
        String formatted = WarehouseWorkbenchViewModel.formatMaterialDisplay(view);
        assertTrue(formatted.contains("VEKA-103.211"));
        assertTrue(formatted.contains("Профиль VEKA Softline"));
        assertTrue(formatted.contains("Белый"));
    }

    @Test
    void receiptOperatorWithoutStockViewCanLoadCatalogueAndSubmitReceipt() {
        UUID warehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        WarehouseView warehouse = new WarehouseView(warehouseId, "WH-1", "Main", true);
        StorageCellView cell = new StorageCellView(cellId, warehouseId, "A-01", true);
        api.warehouses.add(warehouse);
        api.cellsByWarehouse.put(warehouseId, new ArrayList<>(List.of(cell)));

        auth.allowed = Set.of(UiShellScreens.WAREHOUSE_RECEIPT_PERMISSION);
        viewModel.refreshPermissions();
        assertFalse(viewModel.canViewProperty().get());
        assertTrue(viewModel.canReceiptProperty().get());

        viewModel.selectSection(WarehouseSection.RECEIPT);
        assertEquals(1, api.listWarehousesCalls);
        assertFalse(viewModel.warehouseChoices().isEmpty());

        viewModel.receiptWarehouseProperty().set(WarehouseChoice.from(warehouse));
        assertEquals(1, api.listStorageCellsCalls);
        assertFalse(viewModel.receiptCellChoices().isEmpty());

        viewModel.receiptArticleProperty().set("ALU-6060");
        viewModel.receiptNameProperty().set("Алюминий");
        viewModel.receiptQuantityProperty().set("10");
        viewModel.receiptCellProperty().set(StorageCellChoice.from(cell));
        viewModel.submitReceipt();
        assertEquals(1, api.executeCalls.size());
        assertEquals(OperationKind.RECEIPT, api.executeCalls.get(0).kind());
    }

    @Test
    void stockViewRetainsExtendedDisplayFields() {
        UUID warehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.stockByWarehouse.put(
                warehouseId,
                List.of(
                        StockView.of(
                                UUID.randomUUID(),
                                "ALU-6060",
                                "Алюминий",
                                "Серый",
                                "6000 мм",
                                "шт",
                                "WH-1 — Main",
                                "A-01",
                                BigDecimal.TEN,
                                StockStateView.AVAILABLE,
                                warehouseId,
                                cellId)));
        viewModel.loadWarehouses();
        viewModel.stockWarehouseProperty().set(WarehouseChoice.from(api.warehouses.get(0)));
        viewModel.loadStock();
        StockView row = viewModel.stockRows().get(0);
        assertEquals("ALU-6060", row.article());
        assertEquals("Алюминий", row.materialName());
        assertEquals("Серый", row.color());
        assertEquals("6000 мм", row.size());
        assertEquals("шт", row.unitOfMeasure());
    }

    @Test
    void accessDeniedFromApiIsMapped() {
        api.denyNext = true;
        viewModel.loadWarehouses();
        assertEquals(WarehouseUiErrorMapper.ACCESS_DENIED, viewModel.errorMessageProperty().get());
    }

    private static final class FakeAuthorization implements AuthorizationService {
        private Set<String> allowed;

        private FakeAuthorization(Set<String> allowed) {
            this.allowed = allowed;
        }

        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return allowed.contains(permissionId.value());
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
            if (!hasPermission(permissionId)) {
                throw new AccessDeniedException("denied: " + permissionId.value());
            }
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of();
        }
    }

    private static final class FakeWarehouseApi implements WarehouseApi {
        private final List<WarehouseView> warehouses = new ArrayList<>();
        private final java.util.Map<UUID, List<StorageCellView>> cellsByWarehouse =
                new java.util.HashMap<>();
        private final java.util.Map<UUID, List<StockView>> stockByWarehouse =
                new java.util.HashMap<>();
        private final List<ExecuteOperationCommand> executeCalls = new CopyOnWriteArrayList<>();
        private final List<CreateReservationLinkCommand> createReservationCalls =
                new CopyOnWriteArrayList<>();
        private final List<CreateWarehouseCommand> createWarehouseCalls =
                new CopyOnWriteArrayList<>();
        private final List<CreateStorageCellCommand> createCellCalls =
                new CopyOnWriteArrayList<>();
        private final List<ReservationLinkView> links = new CopyOnWriteArrayList<>();
        private int listWarehousesCalls;
        private int listStorageCellsCalls;
        private int getStockByWarehouseCalls;
        private int listReservationCalls;
        private final List<MaterialReferenceView> materialReferences = new ArrayList<>();
        private final List<TransferRequestView> transferDrafts = new ArrayList<>();
        private final List<UUID> sendTransferCalls = new CopyOnWriteArrayList<>();
        private int listMaterialReferencesCalls;
        private boolean denyNext;

        @Override
        public List<WarehouseView> listWarehouses() {
            listWarehousesCalls++;
            if (denyNext) {
                denyNext = false;
                throw new AccessDeniedException("denied");
            }
            return List.copyOf(warehouses);
        }

        @Override
        public WarehouseView createWarehouse(CreateWarehouseCommand command) {
            createWarehouseCalls.add(command);
            WarehouseView view =
                    new WarehouseView(
                            UUID.randomUUID(), command.code(), command.name(), command.active());
            warehouses.add(view);
            return view;
        }

        @Override
        public List<StorageCellView> listStorageCells(UUID warehouseId) {
            listStorageCellsCalls++;
            return List.copyOf(cellsByWarehouse.getOrDefault(warehouseId, List.of()));
        }

        @Override
        public StorageCellView createStorageCell(CreateStorageCellCommand command) {
            createCellCalls.add(command);
            StorageCellView view =
                    new StorageCellView(
                            UUID.randomUUID(),
                            command.warehouseId(),
                            command.code(),
                            command.active());
            cellsByWarehouse
                    .computeIfAbsent(command.warehouseId(), key -> new ArrayList<>())
                    .add(view);
            return view;
        }

        @Override
        public List<StockView> getStock(String materialCode) {
            return List.of();
        }

        @Override
        public List<StockView> getStock(String materialCode, UUID warehouseId, UUID storageCellId) {
            return List.of();
        }

        @Override
        public List<StockView> getStockByWarehouse(UUID warehouseId) {
            getStockByWarehouseCalls++;
            return stockByWarehouse.getOrDefault(warehouseId, List.of());
        }

        private MaterialChoice addMaterial(String article) {
            MaterialReferenceView view =
                    new MaterialReferenceView(
                            UUID.randomUUID(), article, article, "", "", "");
            materialReferences.add(view);
            return MaterialChoice.from(view);
        }

        @Override
        public List<MaterialReferenceView> listMaterialReferences() {
            listMaterialReferencesCalls++;
            if (denyNext) {
                denyNext = false;
                throw new AccessDeniedException("denied");
            }
            return List.copyOf(materialReferences);
        }

        @Override
        public List<String> listUnitOfMeasures() {
            return List.of("шт.", "м.", "кв.м.", "компл.", "л.", "гр.", "кг.");
        }

        @Override
        public MaterialReferenceDisplayView getMaterialReferenceDisplay(String materialCode) {
            return new MaterialReferenceDisplayView(materialCode, materialCode, "", "", "");
        }

        @Override
        public AvailabilityResult checkAvailability(String materialCode, BigDecimal quantity) {
            return new AvailabilityResult(
                    AvailabilityStatus.AVAILABLE, materialCode, quantity, quantity);
        }

        @Override
        public ReservationLinkView createReservationLink(CreateReservationLinkCommand command) {
            createReservationCalls.add(command);
            ReservationLinkView view =
                    new ReservationLinkView(
                            UUID.randomUUID(),
                            command.materialReferenceId(),
                            "MAT",
                            command.targetType(),
                            command.targetReference(),
                            command.quantity(),
                            Instant.parse("2026-08-09T10:00:00Z"));
            links.add(view);
            return view;
        }

        @Override
        public List<ReservationLinkView> listReservationLinks(String materialCode) {
            listReservationCalls++;
            return links.stream()
                    .filter(link -> link.materialCode().equals(materialCode))
                    .toList();
        }

        @Override
        public List<StockView> getStockByMaterialReferenceId(UUID materialReferenceId) {
            return List.of();
        }

        @Override
        public AvailabilityResult checkAvailability(
                WarehouseApi.MaterialIdentityRequest identity, BigDecimal quantity) {
            return checkAvailability(identity.article(), quantity);
        }

        @Override
        public AvailabilityResult checkAvailability(
                WarehouseApi.MaterialIdentityRequest identity, UUID warehouseId, BigDecimal quantity) {
            return checkAvailability(identity.article(), quantity);
        }

        @Override
        public AvailabilityResult checkAvailability(UUID materialReferenceId, BigDecimal quantity) {
            return new AvailabilityResult(
                    AvailabilityStatus.AVAILABLE, materialReferenceId.toString(), quantity, quantity);
        }

        @Override
        public AvailabilityResult checkAvailability(
                UUID materialReferenceId, UUID warehouseId, BigDecimal quantity) {
            return checkAvailability(materialReferenceId, quantity);
        }

        @Override
        public AvailabilityResult checkAvailabilityByLegacyArticle(
                String materialCode, BigDecimal quantity) {
            return checkAvailability(materialCode, quantity);
        }

        @Override
        public TransferStatusView getTransferStatus(UUID operationId) {
            return new TransferStatusView(
                    operationId,
                    OperationKind.TRANSFER_SEND,
                    "SENT",
                    UUID.randomUUID(),
                    BigDecimal.ONE,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null);
        }

        @Override
        public List<TransferRequestView> listTransferDrafts() {
            return List.copyOf(transferDrafts);
        }

        @Override
        public OperationResult receive(WarehouseApi.ReceiptCommand command) {
            return executeWarehouseOperation(
                    ExecuteOperationCommand.receipt(
                            command.article(),
                            command.name(),
                            command.color(),
                            command.size(),
                            command.unitOfMeasure(),
                            command.quantity(),
                            command.warehouseId(),
                            command.storageCellId()));
        }

        @Override
        public OperationResult consume(WarehouseApi.ConsumptionCommand command) {
            return executeWarehouseOperation(
                    ExecuteOperationCommand.consumption(
                            command.materialReferenceId(),
                            command.quantity(),
                            command.warehouseId(),
                            command.storageCellId()));
        }

        @Override
        public TransferRequestView createTransferDraft(
                WarehouseApi.CreateTransferDraftCommand command) {
            return new TransferRequestView(
                    UUID.randomUUID(),
                    "DRAFT",
                    command.materialReferenceId(),
                    command.quantity(),
                    command.sourceWarehouseId(),
                    command.sourceStorageCellId(),
                    command.destinationWarehouseId(),
                    command.destinationStorageCellId());
        }

        @Override
        public OperationResult sendTransfer(UUID transferDraftOperationId) {
            sendTransferCalls.add(transferDraftOperationId);
            return new OperationResult(
                    transferDraftOperationId,
                    OperationKind.TRANSFER_SEND,
                    "COMPLETED",
                    UUID.randomUUID(),
                    "MAT",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    BigDecimal.ONE);
        }

        @Override
        public OperationResult receiveTransfer(UUID sendOperationId) {
            return new OperationResult(
                    sendOperationId,
                    OperationKind.TRANSFER_RECEIVE,
                    "COMPLETED",
                    UUID.randomUUID(),
                    "MAT",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    BigDecimal.ONE);
        }

        @Override
        public OperationResult executeWarehouseOperation(ExecuteOperationCommand command) {
            executeCalls.add(command);
            return new OperationResult(
                    UUID.randomUUID(),
                    command.kind(),
                    "COMPLETED",
                    command.materialReferenceId() != null
                            ? command.materialReferenceId()
                            : UUID.randomUUID(),
                    command.materialCode() != null ? command.materialCode() : "MAT",
                    command.warehouseId(),
                    command.storageCellId(),
                    command.quantity());
        }
    }
}
