package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserCreationResult;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import com.tmp.security.api.PasswordResetResult;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkbenchUiTestSupport.NoOpWarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.CreateStorageCellCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.UpdateStorageCellCommand;
import com.tmp.warehouse.api.WarehouseApi.UpdateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Stage 3.5.13 Warehouse Settings ViewModel tests. */
class WarehouseSettingsViewModelTest {

    private FakeWarehouseApi api;
    private FakeAuth auth;
    private FakeUsers users;
    private WarehouseSettingsViewModel viewModel;

    @BeforeEach
    void setUp() {
        api = new FakeWarehouseApi();
        auth = new FakeAuth();
        users = new FakeUsers();
        viewModel =
                new WarehouseSettingsViewModel(
                        api, auth, users, Runnable::run, Runnable::run);
    }

    @Test
    void loadsWarehousesAndCreatesWhenPermitted() {
        auth.allow(
                UiShellScreens.WAREHOUSE_STRUCTURE_VIEW_PERMISSION,
                UiShellScreens.WAREHOUSE_STRUCTURE_CREATE_PERMISSION);
        viewModel.onScreenOpened();
        assertEquals(0, viewModel.warehouses().size());

        viewModel.newWarehouseCodeProperty().set("WH1");
        viewModel.newWarehouseNameProperty().set("Main");
        viewModel.createWarehouse();
        assertEquals(1, api.createWarehouseCalls.size());
        assertEquals(1, viewModel.warehouses().size());
        assertEquals(0, api.stockMutationCalls);
    }

    @Test
    void operationalUserCannotMutateStructure() {
        auth.allow(UiShellScreens.WAREHOUSE_VIEW_PERMISSION);
        viewModel.onScreenOpened();
        assertFalse(viewModel.canViewStructureProperty().get());
        assertTrue(viewModel.errorMessageProperty().get().contains("Недостаточно"));

        auth.allow(
                UiShellScreens.WAREHOUSE_STRUCTURE_VIEW_PERMISSION,
                UiShellScreens.WAREHOUSE_VIEW_PERMISSION);
        viewModel.refreshPermissions();
        viewModel.onScreenOpened();
        viewModel.newWarehouseCodeProperty().set("WH1");
        viewModel.newWarehouseNameProperty().set("Main");
        viewModel.createWarehouse();
        assertEquals(0, api.createWarehouseCalls.size());
    }

    @Test
    void updateWarehouseAndCellAndResponsibilityWithoutStockMutation() {
        auth.allow(
                UiShellScreens.WAREHOUSE_STRUCTURE_VIEW_PERMISSION,
                UiShellScreens.WAREHOUSE_STRUCTURE_CREATE_PERMISSION,
                UiShellScreens.WAREHOUSE_STRUCTURE_UPDATE_PERMISSION,
                UiShellScreens.WAREHOUSE_STORAGE_CELL_VIEW_PERMISSION,
                UiShellScreens.WAREHOUSE_STORAGE_CELL_CREATE_PERMISSION,
                UiShellScreens.WAREHOUSE_STORAGE_CELL_UPDATE_PERMISSION);
        WarehouseView wh =
                api.createWarehouse(new CreateWarehouseCommand("WH1", "Main", true));
        viewModel.onScreenOpened();
        viewModel.selectedWarehouseProperty().set(wh);
        viewModel.editWarehouseNameProperty().set("Main Renamed");
        viewModel.editWarehouseActiveProperty().set(false);
        viewModel.saveSelectedWarehouse();
        assertEquals(1, api.updateWarehouseCalls.size());
        assertFalse(api.updateWarehouseCalls.get(0).active());

        viewModel.selectSection(WarehouseSettingsViewModel.SettingsSection.CELLS);
        viewModel.setCellsWarehouse(wh);
        viewModel.newCellCodeProperty().set("A-1");
        viewModel.createCell();
        assertEquals(1, api.createCellCalls.size());

        StorageCellView cell = viewModel.cells().get(0);
        viewModel.selectedCellProperty().set(cell);
        viewModel.editCellActiveProperty().set(false);
        viewModel.saveSelectedCell();
        assertEquals(1, api.updateCellCalls.size());
        assertFalse(api.updateCellCalls.get(0).active());

        UserSummary user = users.addActive("clerk", "Clerk");
        viewModel.selectSection(WarehouseSettingsViewModel.SettingsSection.RESPONSIBILITIES);
        viewModel.setResponsibilityWarehouse(wh);
        assertEquals(1, viewModel.responsibilities().size());
        viewModel.setResponsibilityAssigned(viewModel.responsibilities().get(0), true);
        assertTrue(api.assigned.get(wh.warehouseId()).contains(user.id().value()));
        viewModel.setResponsibilityAssigned(viewModel.responsibilities().get(0), false);
        assertTrue(api.assigned.get(wh.warehouseId()).isEmpty());
        assertEquals(0, api.stockMutationCalls);
    }

    @Test
    void doubleSubmitGuardedWhileCommandInFlight() {
        auth.allow(
                UiShellScreens.WAREHOUSE_STRUCTURE_VIEW_PERMISSION,
                UiShellScreens.WAREHOUSE_STRUCTURE_CREATE_PERMISSION);
        viewModel.commandInFlightProperty().set(true);
        viewModel.newWarehouseCodeProperty().set("WH1");
        viewModel.newWarehouseNameProperty().set("Main");
        viewModel.createWarehouse();
        assertEquals(0, api.createWarehouseCalls.size());
    }

    private static final class FakeAuth implements AuthorizationService {
        private Set<String> allowed = Set.of();

        void allow(String... permissions) {
            allowed = Set.of(permissions);
        }

        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return allowed.contains(permissionId.value());
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
            if (!hasPermission(permissionId)) {
                throw new AccessDeniedException("denied");
            }
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of();
        }
    }

    private static final class FakeUsers implements UserAdministrationService {
        private final List<UserSummary> users = new ArrayList<>();

        UserSummary addActive(String login, String name) {
            UserSummary summary =
                    new UserSummary(
                            UserId.of(UUID.randomUUID()),
                            Login.of(login),
                            DisplayName.of(name),
                            "ACTIVE",
                            0L,
                            Instant.parse("2026-09-11T10:00:00Z"),
                            Instant.parse("2026-09-11T10:00:00Z"));
            users.add(summary);
            return summary;
        }

        @Override
        public UserCreationResult createUser(Login login, DisplayName displayName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserSummary updateUser(UserId userId, Login login, DisplayName displayName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserSummary deleteUser(UserId userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<UserSummary> listUsers(int pageIndex, int pageSize, String statusFilter) {
            return List.copyOf(users);
        }

        @Override
        public PasswordResetResult requestPasswordReset(UserId userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<UserSummary> searchUsers(String query, int limit) {
            return List.copyOf(users);
        }

        @Override
        public void changeOwnPassword(char[] currentPassword, char[] newPassword) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeWarehouseApi extends NoOpWarehouseApi {
        private final List<WarehouseView> warehouses = new ArrayList<>();
        private final Map<UUID, List<StorageCellView>> cells = new HashMap<>();
        private final Map<UUID, Set<UUID>> assigned = new HashMap<>();
        private final List<CreateWarehouseCommand> createWarehouseCalls = new ArrayList<>();
        private final List<UpdateWarehouseCommand> updateWarehouseCalls = new ArrayList<>();
        private final List<CreateStorageCellCommand> createCellCalls = new ArrayList<>();
        private final List<UpdateStorageCellCommand> updateCellCalls = new ArrayList<>();
        private int stockMutationCalls;

        @Override
        public List<WarehouseView> listWarehouses() {
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
        public WarehouseView updateWarehouse(UpdateWarehouseCommand command) {
            updateWarehouseCalls.add(command);
            warehouses.removeIf(w -> w.warehouseId().equals(command.warehouseId()));
            WarehouseView view =
                    new WarehouseView(
                            command.warehouseId(),
                            command.code(),
                            command.name(),
                            command.active());
            warehouses.add(view);
            return view;
        }

        @Override
        public List<StorageCellView> listStorageCells(UUID warehouseId) {
            return List.copyOf(cells.getOrDefault(warehouseId, List.of()));
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
            cells.computeIfAbsent(command.warehouseId(), id -> new ArrayList<>()).add(view);
            return view;
        }

        @Override
        public StorageCellView updateStorageCell(UpdateStorageCellCommand command) {
            updateCellCalls.add(command);
            for (List<StorageCellView> list : cells.values()) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).storageCellId().equals(command.storageCellId())) {
                        StorageCellView updated =
                                new StorageCellView(
                                        command.storageCellId(),
                                        list.get(i).warehouseId(),
                                        command.code(),
                                        command.active());
                        list.set(i, updated);
                        return updated;
                    }
                }
            }
            throw new IllegalArgumentException("cell not found");
        }

        @Override
        public List<UUID> listResponsibleUserIds(UUID warehouseId) {
            return List.copyOf(assigned.getOrDefault(warehouseId, Set.of()));
        }

        @Override
        public void assignUserToWarehouse(UUID warehouseId, UUID userId) {
            assigned.computeIfAbsent(warehouseId, id -> new HashSet<>()).add(userId);
        }

        @Override
        public void removeUserFromWarehouse(UUID warehouseId, UUID userId) {
            assigned.computeIfAbsent(warehouseId, id -> new HashSet<>()).remove(userId);
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.OperationResult receive(
                com.tmp.warehouse.api.WarehouseApi.ReceiptCommand command) {
            stockMutationCalls++;
            return super.receive(command);
        }
    }
}
