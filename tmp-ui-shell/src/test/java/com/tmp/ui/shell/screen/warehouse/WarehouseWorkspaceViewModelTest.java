package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.OperationResult;
import com.tmp.warehouse.api.WarehouseApi.WarehouseMaterialStockDetailsView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockCellView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockPage;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockSummaryView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WarehouseWorkspaceViewModelTest {

    private FakeWarehouseApi api;
    private FakeAuthorization auth;
    private WarehouseWorkspaceViewModel viewModel;

    @BeforeEach
    void setUp() {
        api = new FakeWarehouseApi();
        auth = new FakeAuthorization(Set.of(UiShellScreens.WAREHOUSE_VIEW_PERMISSION));
        viewModel = new WarehouseWorkspaceViewModel(api, auth, Runnable::run, Runnable::run);
    }

    @Test
    void firstLoadQueriesSummariesThroughPublicApi() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.stockPages.add(page(List.of(summary(warehouseId, "A-1", BigDecimal.TEN)), 0, 1));

        viewModel.onScreenOpened();

        assertEquals(1, api.listMyWarehousesCalls);
        assertEquals(1, api.listStockSummariesCalls.size());
        assertEquals(1, viewModel.tableRows().size());
        assertInstanceOf(WarehouseWorkspaceViewModel.SummaryRow.class, viewModel.tableRows().get(0));
        assertTrue(viewModel.errorMessageProperty().get().isBlank());
    }

    @Test
    void singleWarehouseAutoSelectedWithoutAllOption() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.stockPages.add(emptyPage());

        viewModel.onScreenOpened();

        assertEquals(1, viewModel.warehouseFilterOptions().size());
        assertFalse(viewModel.warehouseFilterOptions().stream().anyMatch(o -> o.isAll()));
        assertEquals(warehouseId, viewModel.selectedWarehouseFilterProperty().get().warehouseId());
        assertFalse(viewModel.showWarehouseColumnProperty().get());
    }

    @Test
    void multipleWarehousesIncludeAllMyWarehousesChoice() {
        api.warehouses.add(new WarehouseView(UUID.randomUUID(), "WH-1", "One", true));
        api.warehouses.add(new WarehouseView(UUID.randomUUID(), "WH-2", "Two", true));
        api.stockPages.add(emptyPage());

        viewModel.onScreenOpened();

        assertEquals(3, viewModel.warehouseFilterOptions().size());
        assertTrue(viewModel.warehouseFilterOptions().stream().anyMatch(WarehouseWorkspaceViewModel.WarehouseFilterOption::isAll));
        assertTrue(viewModel.selectedWarehouseFilterProperty().get().isAll());
        assertTrue(viewModel.showWarehouseColumnProperty().get());
    }

    @Test
    void switchingWarehouseReloadsSummaries() {
        UUID wh1 = UUID.randomUUID();
        UUID wh2 = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(wh1, "WH-1", "One", true));
        api.warehouses.add(new WarehouseView(wh2, "WH-2", "Two", true));
        api.stockPages.add(emptyPage());
        viewModel.onScreenOpened();
        int callsAfterOpen = api.listStockSummariesCalls.size();

        WarehouseWorkspaceViewModel.WarehouseFilterOption second =
                viewModel.warehouseFilterOptions().stream()
                        .filter(o -> wh2.equals(o.warehouseId()))
                        .findFirst()
                        .orElseThrow();
        viewModel.selectWarehouseFilter(second);

        assertEquals(callsAfterOpen + 1, api.listStockSummariesCalls.size());
        assertEquals(wh2, api.listStockSummariesCalls.getLast().warehouseId());
    }

    @Test
    void searchCommitReloadsWithTrimmedQuery() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.stockPages.add(emptyPage());
        viewModel.onScreenOpened();

        viewModel.searchInputProperty().set("  profile  ");
        viewModel.commitSearch();

        assertEquals("profile", api.listStockSummariesCalls.getLast().search());
        assertEquals("По вашему запросу ничего не найдено", viewModel.statusMessageProperty().get());
    }

    @Test
    void paginationUsesPageSizeFifty() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.stockPages.add(page(List.of(), 0, 120));
        viewModel.onScreenOpened();

        viewModel.nextPage();

        assertEquals(1, viewModel.pageIndexProperty().get());
        assertEquals(50, api.listStockSummariesCalls.getLast().pageSize());
        assertTrue(viewModel.canGoPreviousProperty().get());
        assertTrue(viewModel.canGoNextProperty().get());
    }

    @Test
    void expandAndCollapseInsertsAndRemovesCellRows() {
        UUID warehouseId = UUID.randomUUID();
        UUID materialId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.stockPages.add(
                page(
                        List.of(
                                new WarehouseStockSummaryView(
                                        warehouseId,
                                        materialId,
                                        "ART-1",
                                        "Name",
                                        "",
                                        "",
                                        "шт",
                                        BigDecimal.TEN)),
                        0,
                        1));
        api.breakdowns.put(
                warehouseId + ":" + materialId,
                new WarehouseMaterialStockDetailsView(
                        warehouseId,
                        materialId,
                        BigDecimal.TEN,
                        List.of(new WarehouseStockCellView(UUID.randomUUID(), "A-01", BigDecimal.TEN))));
        viewModel.onScreenOpened();

        WarehouseWorkspaceViewModel.SummaryRow summary =
                (WarehouseWorkspaceViewModel.SummaryRow) viewModel.tableRows().get(0);
        viewModel.toggleExpand(summary);

        assertEquals(1, api.getStockCellBreakdownCalls.size());
        assertEquals(2, viewModel.tableRows().size());
        assertInstanceOf(WarehouseWorkspaceViewModel.CellDetailRow.class, viewModel.tableRows().get(1));

        viewModel.toggleExpand(summary);
        assertEquals(1, viewModel.tableRows().size());
    }

    @Test
    void emptyStockAndAccessDeniedStates() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.stockPages.add(emptyPage());
        viewModel.onScreenOpened();
        assertEquals(
                "На выбранном складе нет доступных остатков",
                viewModel.statusMessageProperty().get());

        api.denyNextStock = true;
        viewModel.commitSearch();
        assertEquals(WarehouseUiErrorMapper.ACCESS_DENIED, viewModel.errorMessageProperty().get());
    }

    @Test
    void staleAsyncStockResponseIsIgnored() throws InterruptedException {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.stockDelayMs = 150;
        api.stockPages.add(page(List.of(summary(warehouseId, "OLD", BigDecimal.ONE)), 0, 1));
        api.stockPages.add(
                page(List.of(summary(warehouseId, "NEW", BigDecimal.TEN)), 0, 1));

        Executor background = Executors.newCachedThreadPool();
        WarehouseWorkspaceViewModel asyncVm =
                new WarehouseWorkspaceViewModel(api, auth, background, Runnable::run);
        asyncVm.onScreenOpened();
        Thread.sleep(20);
        asyncVm.commitSearch();
        Thread.sleep(400);

        assertEquals(1, asyncVm.tableRows().size());
        WarehouseWorkspaceViewModel.SummaryRow row =
                (WarehouseWorkspaceViewModel.SummaryRow) asyncVm.tableRows().get(0);
        assertEquals("NEW", row.article());
    }

    @Test
    void cannotSelectInaccessibleWarehouse() {
        UUID allowed = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(allowed, "WH-1", "Main", true));
        api.stockPages.add(emptyPage());
        viewModel.onScreenOpened();

        WarehouseWorkspaceViewModel.WarehouseFilterOption foreignOption =
                new WarehouseWorkspaceViewModel.WarehouseFilterOption(foreign, "Foreign");
        viewModel.selectWarehouseFilter(foreignOption);

        assertEquals(allowed, viewModel.selectedWarehouseFilterProperty().get().warehouseId());
    }

    @Test
    void noMutationApiIsCalled() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.stockPages.add(emptyPage());
        viewModel.onScreenOpened();
        viewModel.commitSearch();
        viewModel.nextPage();

        assertEquals(0, api.executeCalls);
        assertEquals(0, api.createWarehouseCalls);
    }

    private static WarehouseStockPage emptyPage() {
        return page(List.of(), 0, 0);
    }

    private static WarehouseStockPage page(
            List<WarehouseStockSummaryView> content, int pageIndex, long total) {
        return WarehouseStockPage.of(content, pageIndex, WarehouseWorkspaceViewModel.PAGE_SIZE, total);
    }

    private static WarehouseStockSummaryView summary(UUID warehouseId, String article, BigDecimal qty) {
        return new WarehouseStockSummaryView(
                warehouseId,
                UUID.randomUUID(),
                article,
                article,
                "",
                "",
                "шт",
                qty);
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
                throw new AccessDeniedException("denied");
            }
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of();
        }
    }

    private static final class FakeWarehouseApi extends WarehouseWorkbenchUiTestSupport.NoOpWarehouseApi {
        private final List<WarehouseView> warehouses = new ArrayList<>();
        private final List<WarehouseStockPage> stockPages = new ArrayList<>();
        private final AtomicInteger stockPageCursor = new AtomicInteger();
        private final java.util.Map<String, WarehouseMaterialStockDetailsView> breakdowns =
                new java.util.HashMap<>();
        private final List<StockSummaryCall> listStockSummariesCalls = new CopyOnWriteArrayList<>();
        private final List<BreakdownCall> getStockCellBreakdownCalls = new CopyOnWriteArrayList<>();
        int listMyWarehousesCalls;
        int executeCalls;
        int createWarehouseCalls;
        boolean denyNextStock;
        long stockDelayMs;

        @Override
        public List<WarehouseView> listWarehouses() {
            return List.copyOf(warehouses);
        }

        @Override
        public List<WarehouseView> listMyWarehouses() {
            listMyWarehousesCalls++;
            return List.copyOf(warehouses);
        }

        @Override
        public WarehouseStockPage listStockSummaries(
                UUID warehouseId, String search, int pageIndex, int pageSize) {
            if (denyNextStock) {
                denyNextStock = false;
                throw new AccessDeniedException("denied");
            }
            int index = stockPageCursor.getAndIncrement();
            if (index == 0 && stockDelayMs > 0) {
                try {
                    Thread.sleep(stockDelayMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            listStockSummariesCalls.add(new StockSummaryCall(warehouseId, search, pageIndex, pageSize));
            index = Math.min(index, Math.max(stockPages.size() - 1, 0));
            if (stockPages.isEmpty()) {
                return WarehouseStockPage.of(List.of(), pageIndex, pageSize, 0);
            }
            return stockPages.get(index);
        }

        @Override
        public WarehouseMaterialStockDetailsView getStockCellBreakdown(
                UUID warehouseId, UUID materialReferenceId) {
            getStockCellBreakdownCalls.add(new BreakdownCall(warehouseId, materialReferenceId));
            WarehouseMaterialStockDetailsView details =
                    breakdowns.get(warehouseId + ":" + materialReferenceId);
            if (details == null) {
                throw new IllegalStateException("breakdown not stubbed");
            }
            return details;
        }

        @Override
        public OperationResult executeWarehouseOperation(ExecuteOperationCommand command) {
            executeCalls++;
            throw new UnsupportedOperationException();
        }

        @Override
        public WarehouseView createWarehouse(CreateWarehouseCommand command) {
            createWarehouseCalls++;
            throw new UnsupportedOperationException();
        }

        private record StockSummaryCall(UUID warehouseId, String search, int pageIndex, int pageSize) {}

        private record BreakdownCall(UUID warehouseId, UUID materialReferenceId) {}
    }
}
