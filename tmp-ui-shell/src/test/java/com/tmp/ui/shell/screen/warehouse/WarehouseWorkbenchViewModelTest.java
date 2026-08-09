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
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.OperationKind;
import com.tmp.warehouse.api.WarehouseApi.OperationResult;
import com.tmp.warehouse.api.WarehouseApi.ReservationLinkView;
import com.tmp.warehouse.api.WarehouseApi.ReservationTargetTypeView;
import com.tmp.warehouse.api.WarehouseApi.StockStateView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
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
                UiShellScreens.WAREHOUSE_RESERVATION_PERMISSION));
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
        api.stockByWarehouse.put(
                warehouseId,
                List.of(
                        new StockView(
                                "ALU-6060",
                                warehouseId,
                                UUID.randomUUID(),
                                BigDecimal.TEN,
                                StockStateView.AVAILABLE)));
        viewModel.stockWarehouseIdProperty().set(warehouseId.toString());
        viewModel.loadStock();
        assertEquals(1, viewModel.stockRows().size());
        assertEquals("ALU-6060", viewModel.stockRows().get(0).materialCode());
        assertEquals(1, api.getStockByWarehouseCalls);
    }

    @Test
    void receiptCallsExecuteWarehouseOperation() {
        UUID warehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        viewModel.receiptMaterialProperty().set("ALU-6060");
        viewModel.receiptQuantityProperty().set("12.5");
        viewModel.receiptWarehouseIdProperty().set(warehouseId.toString());
        viewModel.receiptCellIdProperty().set(cellId.toString());
        viewModel.submitReceipt();
        assertEquals(1, api.executeCalls.size());
        ExecuteOperationCommand command = api.executeCalls.get(0);
        assertEquals(OperationKind.RECEIPT, command.kind());
        assertEquals("ALU-6060", command.materialCode());
        assertEquals(0, command.quantity().compareTo(new BigDecimal("12.5")));
        assertTrue(viewModel.statusMessageProperty().get().contains("Поступление"));
    }

    @Test
    void moveTransferConsumptionAdjustmentCallPublicApiWithCapabilities() {
        UUID wh = UUID.randomUUID();
        UUID cell = UUID.randomUUID();
        UUID destWh = UUID.randomUUID();
        UUID destCell = UUID.randomUUID();

        viewModel.moveMaterialProperty().set("M1");
        viewModel.moveQuantityProperty().set("1");
        viewModel.moveSourceWarehouseIdProperty().set(wh.toString());
        viewModel.moveSourceCellIdProperty().set(cell.toString());
        viewModel.moveDestWarehouseIdProperty().set(destWh.toString());
        viewModel.moveDestCellIdProperty().set(destCell.toString());
        viewModel.submitMove();

        viewModel.transferMaterialProperty().set("M1");
        viewModel.transferQuantityProperty().set("2");
        viewModel.transferSourceWarehouseIdProperty().set(wh.toString());
        viewModel.transferSourceCellIdProperty().set(cell.toString());
        viewModel.transferDestWarehouseIdProperty().set(destWh.toString());
        viewModel.submitTransferSend();

        viewModel.consumptionMaterialProperty().set("M1");
        viewModel.consumptionQuantityProperty().set("3");
        viewModel.consumptionWarehouseIdProperty().set(wh.toString());
        viewModel.consumptionCellIdProperty().set(cell.toString());
        viewModel.consumptionBasisProperty().set("Производство");
        viewModel.submitConsumption();

        viewModel.adjustmentMaterialProperty().set("M1");
        viewModel.adjustmentQuantityDeltaProperty().set("-1");
        viewModel.adjustmentWarehouseIdProperty().set(wh.toString());
        viewModel.adjustmentCellIdProperty().set(cell.toString());
        viewModel.adjustmentReasonProperty().set("Инвентаризация");
        viewModel.submitAdjustment();

        assertEquals(4, api.executeCalls.size());
        assertEquals(OperationKind.MOVE, api.executeCalls.get(0).kind());
        assertEquals(OperationKind.TRANSFER_SEND, api.executeCalls.get(1).kind());
        assertEquals(OperationKind.CONSUMPTION, api.executeCalls.get(2).kind());
        assertEquals(OperationKind.ADJUSTMENT, api.executeCalls.get(3).kind());
        assertTrue(viewModel.statusMessageProperty().get().contains("причина=Инвентаризация"));
    }

    @Test
    void reservationSectionUsesListAndCreateThroughPublicApi() {
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
        private final java.util.Map<UUID, List<StockView>> stockByWarehouse =
                new java.util.HashMap<>();
        private final List<ExecuteOperationCommand> executeCalls = new CopyOnWriteArrayList<>();
        private final List<CreateReservationLinkCommand> createReservationCalls =
                new CopyOnWriteArrayList<>();
        private final List<ReservationLinkView> links = new CopyOnWriteArrayList<>();
        private int listWarehousesCalls;
        private int getStockByWarehouseCalls;
        private int listReservationCalls;
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
                            command.materialCode(),
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
        public OperationResult executeWarehouseOperation(ExecuteOperationCommand command) {
            executeCalls.add(command);
            return new OperationResult(
                    UUID.randomUUID(),
                    command.kind(),
                    "COMPLETED",
                    command.materialCode(),
                    command.warehouseId(),
                    command.storageCellId(),
                    command.quantity());
        }
    }
}
