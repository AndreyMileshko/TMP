package com.tmp.ui.shell.screen.warehouse;

import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.AvailabilityResult;
import com.tmp.warehouse.api.WarehouseApi.CreateReservationLinkCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateStorageCellCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceDisplayView;
import com.tmp.warehouse.api.WarehouseApi.OperationResult;
import com.tmp.warehouse.api.WarehouseApi.ReservationLinkView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Shared fakes for Warehouse workbench UI tests. */
final class WarehouseWorkbenchUiTestSupport {

    private WarehouseWorkbenchUiTestSupport() {}

    static final class NoOpWarehouseApi implements WarehouseApi {
        @Override
        public List<WarehouseView> listWarehouses() {
            return List.of();
        }

        @Override
        public WarehouseView createWarehouse(CreateWarehouseCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StorageCellView> listStorageCells(UUID warehouseId) {
            return List.of();
        }

        @Override
        public StorageCellView createStorageCell(CreateStorageCellCommand command) {
            throw new UnsupportedOperationException();
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
            return List.of();
        }

        @Override
        public List<MaterialReferenceView> listMaterialReferences() {
            return List.of();
        }

        @Override
        public MaterialReferenceDisplayView getMaterialReferenceDisplay(String materialCode) {
            return new MaterialReferenceDisplayView(materialCode, "", "", "", "");
        }

        @Override
        public AvailabilityResult checkAvailability(String materialCode, BigDecimal quantity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReservationLinkView createReservationLink(CreateReservationLinkCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReservationLinkView> listReservationLinks(String materialCode) {
            return List.of();
        }

        @Override
        public OperationResult executeWarehouseOperation(ExecuteOperationCommand command) {
            throw new UnsupportedOperationException();
        }
    }

    static final class AllowAllAuthorization implements AuthorizationService {
        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return true;
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
            // allow
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of(
                    PermissionId.of(UiShellScreens.WAREHOUSE_VIEW_PERMISSION),
                    PermissionId.of(UiShellScreens.WAREHOUSE_RECEIPT_PERMISSION),
                    PermissionId.of(UiShellScreens.WAREHOUSE_MOVE_PERMISSION),
                    PermissionId.of(UiShellScreens.WAREHOUSE_TRANSFER_PERMISSION),
                    PermissionId.of(UiShellScreens.WAREHOUSE_CONSUMPTION_PERMISSION),
                    PermissionId.of(UiShellScreens.WAREHOUSE_ADJUSTMENT_PERMISSION),
                    PermissionId.of(UiShellScreens.WAREHOUSE_RESERVATION_PERMISSION));
        }
    }
}
