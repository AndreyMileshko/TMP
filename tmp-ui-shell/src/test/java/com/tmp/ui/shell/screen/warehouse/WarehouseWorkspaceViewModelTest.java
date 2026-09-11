package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.screen.warehouse.WarehouseUiErrorMapper;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.ReceiveAllocationEditRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.ReturnAllocationEditRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.SourceAllocationEditRow;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceView;
import com.tmp.warehouse.api.WarehouseApi.OperationResult;
import com.tmp.warehouse.api.WarehouseApi.ReceiveTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.RejectTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.ReturnTransferMaterialsCommand;
import com.tmp.warehouse.api.WarehouseApi.SendTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.SourceCellSuggestion;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentDestinationAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentLineView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReceiveResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentRejectResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReturnAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReturnPlanItem;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReturnResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSendResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSourceAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSourceSuggestionLine;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseMaterialStockDetailsView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockCellView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockPage;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockSummaryView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskKind;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskState;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    void defaultTabIsTasks() {
        assertEquals(WarehouseWorkspaceViewModel.WorkspaceTab.TASKS, viewModel.selectedTabProperty().get());
    }

    @Test
    void onScreenOpenedLoadsTasksThroughPublicApi() {
        UUID warehouseId = UUID.randomUUID();
        UUID destId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.tasks.add(task(warehouseId, destId, "TR-1"));

        viewModel.onScreenOpened();

        assertEquals(1, api.listMyWarehousesCalls);
        assertEquals(1, api.listMyWarehouseTasksCalls.size());
        assertEquals(0, api.listStockSummariesCalls.size());
        assertEquals(1, viewModel.taskRows().size());
        assertEquals("TR-1", viewModel.taskRows().get(0).documentNumber());
        assertTrue(viewModel.errorMessageProperty().get().isBlank());
    }

    @Test
    void takeInWorkCallsPublicApiAndRefreshesTasks() {
        UUID sourceId = UUID.randomUUID();
        UUID destId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(sourceId, "WH-1", "Main", true));
        auth = new FakeAuthorization(
                Set.of(
                        UiShellScreens.WAREHOUSE_VIEW_PERMISSION,
                        UiShellScreens.WAREHOUSE_TRANSFER_PERMISSION));
        viewModel = new WarehouseWorkspaceViewModel(api, auth, Runnable::run, Runnable::run);
        api.tasks.add(task(docId, sourceId, destId, "TR-2", WarehouseTaskKind.TRANSFER_PREPARATION, WarehouseTaskState.NEW, null));
        viewModel.onScreenOpened();
        viewModel.selectTask(viewModel.taskRows().get(0));

        viewModel.takeSelectedTaskInWork();

        assertEquals(1, api.takeTransferTaskInWorkCalls.size());
        assertEquals(docId, api.takeTransferTaskInWorkCalls.get(0));
        assertEquals(2, api.listMyWarehouseTasksCalls.size());
    }

    @Test
    void switchingTasksAndStockTabsLoadsBothLists() {
        UUID warehouseId = UUID.randomUUID();
        UUID destId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.tasks.add(task(warehouseId, destId, "TR-3"));
        api.stockPages.add(page(List.of(summary(warehouseId, "A-1", BigDecimal.TEN)), 0, 1));
        viewModel.onScreenOpened();
        assertEquals(1, api.listMyWarehouseTasksCalls.size());

        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.STOCK);

        assertEquals(1, api.listStockSummariesCalls.size());
        assertEquals(1, viewModel.tableRows().size());
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.TASKS);
        assertEquals(2, api.listMyWarehouseTasksCalls.size());
    }

    @Test
    void historyTabLoadsThroughPublicApi() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.historyPages.add(historyPage(List.of(historyEntry(warehouseId, "RECEIPT", BigDecimal.TEN)), 0, 1));
        viewModel.onScreenOpened();
        int taskCalls = api.listMyWarehouseTasksCalls.size();

        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);

        assertEquals(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY, viewModel.selectedTabProperty().get());
        assertEquals(taskCalls, api.listMyWarehouseTasksCalls.size());
        assertEquals(0, api.listStockSummariesCalls.size());
        assertEquals(1, api.listHistoryCalls.size());
        assertEquals(warehouseId, api.listHistoryCalls.get(0).warehouseId());
        assertEquals(WarehouseWorkspaceViewModel.HISTORY_PAGE_SIZE, api.listHistoryCalls.get(0).pageSize());
        assertEquals(1, viewModel.historyRows().size());
        assertTrue(viewModel.loadingProperty().get() == false);
    }

    @Test
    void historyLoadingShowsWhileAsyncRequestRuns() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.historyPages.add(emptyHistoryPage());
        java.util.concurrent.atomic.AtomicReference<WarehouseWorkspaceViewModel> ref =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean sawLoading = new java.util.concurrent.atomic.AtomicBoolean();
        Executor background =
                command -> {
                    sawLoading.set(ref.get().loadingProperty().get());
                    command.run();
                };
        WarehouseWorkspaceViewModel asyncVm =
                new WarehouseWorkspaceViewModel(api, auth, background, Runnable::run);
        ref.set(asyncVm);
        asyncVm.onScreenOpened();
        asyncVm.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);

        assertTrue(sawLoading.get());
        assertFalse(asyncVm.loadingProperty().get());
        assertEquals(1, api.listHistoryCalls.size());
    }

    @Test
    void historyRowsMapEntryFields() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        WarehouseApi.WarehouseHistoryEntryView entry =
                new WarehouseApi.WarehouseHistoryEntryView(
                        UUID.randomUUID(),
                        Instant.parse("2026-09-01T10:15:00Z"),
                        "TRANSFER_SEND",
                        "Передача",
                        UUID.randomUUID(),
                        "ART-9",
                        "Profile",
                        "шт",
                        new BigDecimal("-3"),
                        warehouseId,
                        "Main",
                        UUID.randomUUID(),
                        "A-01",
                        UUID.randomUUID(),
                        "Prod",
                        UUID.randomUUID(),
                        "B-02",
                        UUID.randomUUID(),
                        "TR-77",
                        UUID.randomUUID(),
                        "Иванов");
        api.historyPages.add(historyPage(List.of(entry), 0, 1));
        viewModel.onScreenOpened();
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);

        WarehouseWorkspaceViewModel.HistoryRow row = viewModel.historyRows().get(0);
        assertEquals("Передача", row.operationLabel());
        assertTrue(row.materialText().contains("ART-9"));
        assertEquals("-3", row.quantityText());
        assertEquals("Main / A-01", row.sourceText());
        assertEquals("Prod / B-02", row.destinationText());
        assertEquals("TR-77", row.documentText());
        assertEquals("Иванов", row.actorText());
    }

    @Test
    void historyEmptyPeriodMessageWithoutExtraFilters() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.historyPages.add(emptyHistoryPage());
        viewModel.onScreenOpened();
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);

        assertEquals("За выбранный период операций нет", viewModel.statusMessageProperty().get());
    }

    @Test
    void historyEmptyFilterMessageWithMaterialSearch() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.historyPages.add(emptyHistoryPage());
        api.historyPages.add(emptyHistoryPage());
        viewModel.onScreenOpened();
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);
        viewModel.historySearchInputProperty().set("  aluminum  ");
        viewModel.commitHistorySearch();

        assertEquals("aluminum", api.listHistoryCalls.getLast().filter().materialSearch());
        assertEquals(
                "По выбранным условиям ничего не найдено",
                viewModel.statusMessageProperty().get());
    }

    @Test
    void historyErrorUsesUiErrorMapper() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.historyPages.add(emptyHistoryPage());
        viewModel.onScreenOpened();
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);
        api.denyNextHistory = true;
        viewModel.refreshHistory();

        assertEquals(WarehouseUiErrorMapper.ACCESS_DENIED, viewModel.errorMessageProperty().get());
        assertEquals(WarehouseUiErrorMapper.LOAD_FAILED, viewModel.statusMessageProperty().get());
    }

    @Test
    void historyWarehouseChangeReloadsAndResetsPage() {
        UUID wh1 = UUID.randomUUID();
        UUID wh2 = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(wh1, "WH-1", "One", true));
        api.warehouses.add(new WarehouseView(wh2, "WH-2", "Two", true));
        api.historyPages.add(historyPage(List.of(), 0, 80));
        api.historyPages.add(historyPage(List.of(), 0, 80));
        api.historyPages.add(emptyHistoryPage());
        viewModel.onScreenOpened();
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);
        viewModel.nextHistoryPage();
        assertEquals(1, viewModel.historyPageIndexProperty().get());
        int calls = api.listHistoryCalls.size();

        WarehouseWorkspaceViewModel.WarehouseFilterOption second =
                viewModel.warehouseFilterOptions().stream()
                        .filter(o -> wh2.equals(o.warehouseId()))
                        .findFirst()
                        .orElseThrow();
        viewModel.selectWarehouseFilter(second);

        assertEquals(calls + 1, api.listHistoryCalls.size());
        assertEquals(0, viewModel.historyPageIndexProperty().get());
        assertEquals(wh2, api.listHistoryCalls.getLast().warehouseId());
    }

    @Test
    void historyAllModeUsesNullWarehouseId() {
        api.warehouses.add(new WarehouseView(UUID.randomUUID(), "WH-1", "One", true));
        api.warehouses.add(new WarehouseView(UUID.randomUUID(), "WH-2", "Two", true));
        api.historyPages.add(emptyHistoryPage());
        viewModel.onScreenOpened();
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);

        assertEquals(1, api.listHistoryCalls.size());
        assertNull(api.listHistoryCalls.get(0).warehouseId());
    }

    @Test
    void historyPeriodChangeReloads() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.historyPages.add(emptyHistoryPage());
        api.historyPages.add(emptyHistoryPage());
        viewModel.onScreenOpened();
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);
        int calls = api.listHistoryCalls.size();
        java.time.LocalDate from = viewModel.historyFromDateProperty().get().minusDays(5);

        viewModel.setHistoryFromDate(from);

        assertEquals(calls + 1, api.listHistoryCalls.size());
        assertEquals(
                from.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(),
                api.listHistoryCalls.getLast().filter().fromInclusive());
    }

    @Test
    void historyOperationFilterReloads() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.historyPages.add(emptyHistoryPage());
        api.historyPages.add(emptyHistoryPage());
        viewModel.onScreenOpened();
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);

        WarehouseWorkspaceViewModel.HistoryOperationOption receipt =
                viewModel.historyOperationOptions().stream()
                        .filter(o -> "RECEIPT".equals(o.operationType()))
                        .findFirst()
                        .orElseThrow();
        viewModel.selectHistoryOperation(receipt);

        assertEquals("RECEIPT", api.listHistoryCalls.getLast().filter().operationType());
        assertEquals(
                "По выбранным условиям ничего не найдено",
                viewModel.statusMessageProperty().get());
    }

    @Test
    void historyPaginationUsesDefaultPageSize() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.historyPages.add(historyPage(List.of(), 0, 120));
        api.historyPages.add(historyPage(List.of(), 1, 120));
        viewModel.onScreenOpened();
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);

        viewModel.nextHistoryPage();

        assertEquals(1, viewModel.historyPageIndexProperty().get());
        assertEquals(50, api.listHistoryCalls.getLast().pageSize());
        assertTrue(viewModel.historyCanGoPreviousProperty().get());
        assertTrue(viewModel.historyCanGoNextProperty().get());
    }

    @Test
    void historyRefreshReloadsSameFilters() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.historyPages.add(emptyHistoryPage());
        api.historyPages.add(emptyHistoryPage());
        viewModel.onScreenOpened();
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);
        viewModel.historySearchInputProperty().set("pipe");
        viewModel.commitHistorySearch();
        int calls = api.listHistoryCalls.size();

        viewModel.refreshHistory();

        assertEquals(calls + 1, api.listHistoryCalls.size());
        assertEquals("pipe", api.listHistoryCalls.getLast().filter().materialSearch());
    }

    @Test
    void staleAsyncHistoryResponseIsIgnored() throws InterruptedException {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.historyDelayMs = 150;
        api.historyPages.add(
                historyPage(List.of(historyEntry(warehouseId, "RECEIPT", BigDecimal.ONE)), 0, 1));
        api.historyPages.add(
                historyPage(List.of(historyEntry(warehouseId, "MOVE", BigDecimal.TEN)), 0, 1));

        Executor background = Executors.newCachedThreadPool();
        WarehouseWorkspaceViewModel asyncVm =
                new WarehouseWorkspaceViewModel(api, auth, background, Runnable::run);
        asyncVm.onScreenOpened();
        asyncVm.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);
        Thread.sleep(20);
        asyncVm.refreshHistory();
        Thread.sleep(400);

        assertEquals(1, asyncVm.historyRows().size());
        assertEquals("Перемещение", asyncVm.historyRows().get(0).operationLabel());
    }

    @Test
    void switchingTabsPreservesHistoryAndStockState() {
        UUID warehouseId = UUID.randomUUID();
        UUID destId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.tasks.add(task(warehouseId, destId, "TR-H"));
        api.stockPages.add(page(List.of(summary(warehouseId, "A-1", BigDecimal.TEN)), 0, 1));
        api.historyPages.add(
                historyPage(List.of(historyEntry(warehouseId, "RECEIPT", BigDecimal.ONE)), 0, 1));
        viewModel.onScreenOpened();
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.STOCK);
        assertEquals(1, viewModel.tableRows().size());
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);
        assertEquals(1, viewModel.historyRows().size());

        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.STOCK);
        assertEquals(1, viewModel.tableRows().size());
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.TASKS);
        assertFalse(viewModel.taskRows().isEmpty());
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);
        assertEquals(1, viewModel.historyRows().size());
    }

    @Test
    void historyTabDoesNotInvokeMutationCommands() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.historyPages.add(emptyHistoryPage());
        viewModel.onScreenOpened();
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.HISTORY);
        viewModel.refreshHistory();

        assertEquals(0, api.executeCalls);
        assertEquals(0, api.createWarehouseCalls);
        assertEquals(0, api.sendCommands.size());
        assertEquals(0, api.receiveCommands.size());
        assertEquals(0, api.rejectCommands.size());
        assertEquals(0, api.returnCommands.size());
        assertEquals(0, api.takeTransferTaskInWorkCalls.size());
    }

    @Test
    void historyQuantityFormattingUsesSignExceptMove() {
        assertEquals(
                "+5",
                WarehouseWorkspaceViewModel.formatHistoryQuantity("RECEIPT", new BigDecimal("5")));
        assertEquals(
                "-2",
                WarehouseWorkspaceViewModel.formatHistoryQuantity(
                        "TRANSFER_SEND", new BigDecimal("-2")));
        assertEquals(
                "4", WarehouseWorkspaceViewModel.formatHistoryQuantity("MOVE", new BigDecimal("4")));
        assertEquals(
                "4",
                WarehouseWorkspaceViewModel.formatHistoryQuantity("MOVE", new BigDecimal("-4")));
    }

    @Test
    void allWarehousesModeUsesSingleStockSummaryCall() {
        api.warehouses.add(new WarehouseView(UUID.randomUUID(), "WH-1", "One", true));
        api.warehouses.add(new WarehouseView(UUID.randomUUID(), "WH-2", "Two", true));
        api.stockPages.add(emptyPage());
        viewModel.onScreenOpened();
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.STOCK);

        assertEquals(1, api.listStockSummariesCalls.size());
        assertEquals(null, api.listStockSummariesCalls.get(0).warehouseId());
    }

    @Test
    void firstLoadQueriesSummariesThroughPublicApiWhenStockTabSelected() {
        UUID warehouseId = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(warehouseId, "WH-1", "Main", true));
        api.stockPages.add(page(List.of(summary(warehouseId, "A-1", BigDecimal.TEN)), 0, 1));
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.STOCK);

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
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.STOCK);

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
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.STOCK);

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
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.STOCK);
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
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.STOCK);

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
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.STOCK);

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
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.STOCK);

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
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.STOCK);
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
        asyncVm.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.STOCK);
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
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.STOCK);

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
        viewModel.selectTab(WarehouseWorkspaceViewModel.WorkspaceTab.STOCK);
        viewModel.commitSearch();
        viewModel.nextPage();

        assertEquals(0, api.executeCalls);
        assertEquals(0, api.createWarehouseCalls);
    }

    @Test
    void preparationLoadsSuggestionsIntoEditRows() {
        PreparationFixture fx = openPreparation();
        assertEquals(1, viewModel.actionLines().size());
        SourceAllocationEditRow row = (SourceAllocationEditRow) viewModel.actionLines().get(0);
        assertEquals(fx.lineId, row.lineId());
        assertEquals(fx.cellA, row.storageCellProperty().get().id());
        assertEquals("10", row.quantityTextProperty().get());
        assertTrue(viewModel.taskDetailsTextProperty().get().contains("TR-PREP"));
        assertEquals(1, api.suggestCalls.size());
        assertEquals(1, api.listStorageCellsCalls);
    }

    @Test
    void preparationAllowsOverrideSourceCellAndQuantity() {
        PreparationFixture fx = openPreparation();
        SourceAllocationEditRow row = (SourceAllocationEditRow) viewModel.actionLines().get(0);
        StorageCellChoice other =
                viewModel.actionCellChoices().stream()
                        .filter(c -> fx.cellB.equals(c.id()))
                        .findFirst()
                        .orElseThrow();
        row.storageCellProperty().set(other);
        row.quantityTextProperty().set("7");
        assertEquals(fx.cellB, row.storageCellProperty().get().id());
        assertEquals("7", row.quantityTextProperty().get());
        assertTrue(viewModel.canSendSelectedTaskProperty().get());
    }

    @Test
    void preparationFullSendMapsCommandAndReloadsInbox() {
        PreparationFixture fx = openPreparation();
        int tasksBefore = api.listMyWarehouseTasksCalls.size();
        viewModel.sendSelectedTask();

        assertEquals(1, api.sendCommands.size());
        SendTransferDocumentCommand command = api.sendCommands.get(0);
        assertEquals(fx.documentId, command.documentId());
        assertEquals(3L, command.expectedDocumentVersion());
        assertEquals(5L, command.expectedPayloadRevision());
        assertEquals(1, command.sourceAllocations().size());
        TransferDocumentSourceAllocationInput alloc = command.sourceAllocations().get(0);
        assertEquals(fx.lineId, alloc.lineId());
        assertEquals(fx.cellA, alloc.sourceStorageCellId());
        assertEquals(0, new BigDecimal("10").compareTo(alloc.quantity()));
        assertTrue(api.listMyWarehouseTasksCalls.size() > tasksBefore);
        assertTrue(viewModel.statusMessageProperty().get().startsWith("Передано:"));
    }

    @Test
    void preparationPartialSendMapsLowerQtyWithoutInventingContinuation() {
        PreparationFixture fx = openPreparation();
        SourceAllocationEditRow row = (SourceAllocationEditRow) viewModel.actionLines().get(0);
        row.quantityTextProperty().set("4");
        api.sendResultContinuationId = UUID.randomUUID();

        viewModel.sendSelectedTask();

        assertEquals(1, api.sendCommands.size());
        assertEquals(0, new BigDecimal("4").compareTo(api.sendCommands.get(0).sourceAllocations().get(0).quantity()));
        assertTrue(viewModel.statusMessageProperty().get().contains("Создано дополнительное перемещение."));
        assertNull(api.sendCommands.get(0).sourceAllocations().stream()
                .map(TransferDocumentSourceAllocationInput::quantity)
                .filter(q -> q.compareTo(new BigDecimal("6")) == 0)
                .findFirst()
                .orElse(null));
    }

    @Test
    void preparationStaleRevisionShowsStaleAndReloadsTasks() {
        openPreparation();
        int tasksBefore = api.listMyWarehouseTasksCalls.size();
        api.sendThrows = new IllegalStateException("stale document version");

        viewModel.sendSelectedTask();

        assertEquals(1, api.sendCommands.size());
        assertEquals(WarehouseUiErrorMapper.STALE_STATE, viewModel.errorMessageProperty().get());
        assertTrue(api.listMyWarehouseTasksCalls.size() > tasksBefore);
    }

    @Test
    void preparationDoubleSubmitIgnoresSecondWhileInFlight() throws Exception {
        PreparationFixture fx = new PreparationFixture();
        stubPreparation(fx);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        api.sendBlock =
                () -> {
                    started.countDown();
                    release.await(5, TimeUnit.SECONDS);
                };
        java.util.concurrent.ExecutorService background = Executors.newSingleThreadExecutor();
        try {
            auth = transferAuth();
            viewModel = new WarehouseWorkspaceViewModel(api, auth, background, Runnable::run);
            viewModel.onScreenOpened();
            awaitCondition(() -> !viewModel.taskRows().isEmpty(), 5000);
            viewModel.selectTask(viewModel.taskRows().get(0));
            awaitCondition(
                    () ->
                            !viewModel.actionLines().isEmpty()
                                    && viewModel.canSendSelectedTaskProperty().get(),
                    5000);

            viewModel.sendSelectedTask();
            assertTrue(started.await(5, TimeUnit.SECONDS), "send API was not invoked");
            assertTrue(viewModel.commandInFlightProperty().get());
            viewModel.sendSelectedTask();
            release.countDown();
            awaitCondition(() -> !viewModel.commandInFlightProperty().get(), 5000);

            assertEquals(1, api.sendCommands.size());
        } finally {
            release.countDown();
            background.shutdownNow();
        }
    }

    @Test
    void receiptLoadsSentLinesAndAllowsDestinationCell() {
        ReceiptFixture fx = openReceipt();
        assertEquals(1, viewModel.actionLines().size());
        ReceiveAllocationEditRow row = (ReceiveAllocationEditRow) viewModel.actionLines().get(0);
        assertEquals("8", row.referenceQuantityText());
        assertEquals("8", row.quantityTextProperty().get());
        StorageCellChoice dest =
                viewModel.actionCellChoices().stream()
                        .filter(c -> fx.destCell.equals(c.id()))
                        .findFirst()
                        .orElseThrow();
        row.storageCellProperty().set(dest);
        assertTrue(viewModel.canReceiveSelectedTaskProperty().get());
        assertTrue(viewModel.canRejectSelectedTaskProperty().get());
    }

    @Test
    void receiptMultiCellReceiveMapsTwoRowsSameLine() {
        ReceiptFixture fx = openReceipt();
        ReceiveAllocationEditRow first = (ReceiveAllocationEditRow) viewModel.actionLines().get(0);
        first.quantityTextProperty().set("3");
        first.storageCellProperty()
                .set(choice(viewModel.actionCellChoices(), fx.destCell));
        viewModel.addReceiveAllocationForLine(fx.lineId);
        assertEquals(2, viewModel.actionLines().size());
        ReceiveAllocationEditRow second = (ReceiveAllocationEditRow) viewModel.actionLines().get(1);
        second.quantityTextProperty().set("5");
        second.storageCellProperty()
                .set(choice(viewModel.actionCellChoices(), fx.destCellB));

        viewModel.receiveSelectedTask();

        assertEquals(1, api.receiveCommands.size());
        List<TransferDocumentDestinationAllocationInput> allocs =
                api.receiveCommands.get(0).destinationAllocations();
        assertEquals(2, allocs.size());
        assertEquals(fx.lineId, allocs.get(0).lineId());
        assertEquals(fx.lineId, allocs.get(1).lineId());
        assertEquals(fx.destCell, allocs.get(0).destinationStorageCellId());
        assertEquals(fx.destCellB, allocs.get(1).destinationStorageCellId());
    }

    @Test
    void receiptFullAndPartialReceive() {
        ReceiptFixture fx = openReceipt();
        ReceiveAllocationEditRow row = (ReceiveAllocationEditRow) viewModel.actionLines().get(0);
        row.storageCellProperty().set(choice(viewModel.actionCellChoices(), fx.destCell));
        viewModel.receiveSelectedTask();
        assertEquals(1, api.receiveCommands.size());
        assertEquals(0, new BigDecimal("8").compareTo(api.receiveCommands.get(0).destinationAllocations().get(0).quantity()));
        assertTrue(viewModel.statusMessageProperty().get().startsWith("Принято:"));

        openReceipt();
        row = (ReceiveAllocationEditRow) viewModel.actionLines().get(0);
        row.quantityTextProperty().set("2");
        row.storageCellProperty().set(choice(viewModel.actionCellChoices(), fx.destCell));
        viewModel.receiveSelectedTask();
        assertEquals(2, api.receiveCommands.size());
        assertEquals(0, new BigDecimal("2").compareTo(api.receiveCommands.get(1).destinationAllocations().get(0).quantity()));
    }

    @Test
    void receiptBlocksAcceptGreaterThanSent() {
        ReceiptFixture fx = openReceipt();
        ReceiveAllocationEditRow row = (ReceiveAllocationEditRow) viewModel.actionLines().get(0);
        row.quantityTextProperty().set("9");
        row.storageCellProperty().set(choice(viewModel.actionCellChoices(), fx.destCell));
        assertFalse(viewModel.canReceiveSelectedTaskProperty().get());
    }

    /**
     * After Stage 3.5.7 shortfall send (requested 100, sent 98), Warehouse shrinks the POSTED
     * document line to actual sent. Receipt UI must use that persisted line quantity — not
     * reconstruct {@code requested - continuation}.
     */
    @Test
    void receiptAfterShortfallUsesPersistedSentQuantityNotOriginalRequest() {
        auth = transferAuth();
        viewModel = new WarehouseWorkspaceViewModel(api, auth, Runnable::run, Runnable::run);
        UUID documentId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID destCell = UUID.randomUUID();
        // Post-shortfall Warehouse read state only (actual sent = 98). Original request 100 is
        // intentionally absent — UI must not need it or continuation qty.
        api.warehouses.add(new WarehouseView(destId, "DST", "Dest", true));
        api.materials.add(new MaterialReferenceView(materialId, "ART-SF", "Profile", "", "", "м"));
        api.cellsByWarehouse.put(
                destId, List.of(new StorageCellView(destCell, destId, "D-01", true)));
        api.documents.put(
                documentId,
                document(
                        documentId,
                        sourceId,
                        destId,
                        List.of(
                                new TransferDocumentLineView(
                                        lineId, materialId, new BigDecimal("98"), 1)),
                        3L,
                        6L,
                        20L));
        api.tasks.add(
                task(
                        documentId,
                        sourceId,
                        destId,
                        "TR-SF-RCV",
                        WarehouseTaskKind.TRANSFER_RECEIPT,
                        WarehouseTaskState.IN_WORK,
                        UUID.randomUUID()));

        viewModel.onScreenOpened();
        viewModel.selectTask(viewModel.taskRows().get(0));

        assertEquals(1, viewModel.actionLines().size());
        ReceiveAllocationEditRow row = (ReceiveAllocationEditRow) viewModel.actionLines().get(0);
        assertEquals(0, new BigDecimal("98").compareTo(row.sentQuantity()));
        assertEquals("98", row.referenceQuantityText());
        assertEquals("98", row.quantityTextProperty().get());

        StorageCellChoice cell = choice(viewModel.actionCellChoices(), destCell);
        row.storageCellProperty().set(cell);

        row.quantityTextProperty().set("98");
        assertTrue(viewModel.canReceiveSelectedTaskProperty().get(), "receive 98 must be valid");

        row.quantityTextProperty().set("97");
        assertTrue(viewModel.canReceiveSelectedTaskProperty().get(), "receive 97 must be valid");

        row.quantityTextProperty().set("99");
        assertFalse(viewModel.canReceiveSelectedTaskProperty().get(), "receive 99 must be invalid");

        row.quantityTextProperty().set("100");
        assertFalse(viewModel.canReceiveSelectedTaskProperty().get(), "receive 100 must be invalid");
    }

    @Test
    void rejectBlankReasonBlockedAndTrimmedReasonPassed() {
        openReceipt();
        int before = api.rejectCommands.size();
        viewModel.rejectSelectedTask("   ");
        assertEquals(before, api.rejectCommands.size());
        assertEquals(WarehouseUiErrorMapper.VALIDATION, viewModel.errorMessageProperty().get());

        viewModel.rejectSelectedTask("  брак  ");
        assertEquals(1, api.rejectCommands.size());
        assertEquals("брак", api.rejectCommands.get(0).rejectionReason());
    }

    @Test
    void returnLoadsOutstandingDefaultsAndIgnoresQuantityEdit() {
        ReturnFixture fx = openReturn();
        assertEquals(1, viewModel.actionLines().size());
        ReturnAllocationEditRow row = (ReturnAllocationEditRow) viewModel.actionLines().get(0);
        assertEquals("6", row.referenceQuantityText());
        assertEquals(fx.defaultCell, row.storageCellProperty().get().id());
        assertFalse(row.quantityEditable());
        row.quantityTextProperty().set("999");
        assertEquals("6", row.quantityTextProperty().get());
        StorageCellChoice alt = choice(viewModel.actionCellChoices(), fx.altCell);
        row.storageCellProperty().set(alt);
        assertEquals(fx.altCell, row.storageCellProperty().get().id());
    }

    @Test
    void returnCommandUsesEmptyAllocationsForDefaultsAndFullListOnOverride() {
        ReturnFixture fx = openReturn();
        viewModel.returnSelectedTask();
        assertEquals(1, api.returnCommands.size());
        assertTrue(api.returnCommands.get(0).returnAllocations().isEmpty());

        openReturn();
        ReturnAllocationEditRow row = (ReturnAllocationEditRow) viewModel.actionLines().get(0);
        row.storageCellProperty().set(choice(viewModel.actionCellChoices(), fx.altCell));
        viewModel.returnSelectedTask();
        assertEquals(2, api.returnCommands.size());
        List<TransferDocumentReturnAllocationInput> allocs =
                api.returnCommands.get(1).returnAllocations();
        assertEquals(1, allocs.size());
        assertEquals(fx.lineId, allocs.get(0).lineId());
        assertEquals(fx.altCell, allocs.get(0).returnStorageCellId());
        assertEquals(0, new BigDecimal("6").compareTo(allocs.get(0).quantity()));
    }

    @Test
    void returnSuccessReloadsAndStaleHandled() {
        openReturn();
        api.tasks.clear();
        int before = api.listMyWarehouseTasksCalls.size();
        viewModel.returnSelectedTask();
        assertTrue(api.listMyWarehouseTasksCalls.size() > before);
        assertTrue(viewModel.taskRows().isEmpty());

        ReturnFixture fx = openReturn();
        api.returnThrows = new IllegalStateException("stale operational revision");
        before = api.listMyWarehouseTasksCalls.size();
        viewModel.returnSelectedTask();
        assertEquals(WarehouseUiErrorMapper.STALE_STATE, viewModel.errorMessageProperty().get());
        assertTrue(api.listMyWarehouseTasksCalls.size() > before);
        assertNotNull(fx.documentId);
    }

    @Test
    void inWorkWorkerDisplayUsesPrefix() {
        UUID sourceId = UUID.randomUUID();
        UUID destId = UUID.randomUUID();
        UUID worker = UUID.randomUUID();
        api.warehouses.add(new WarehouseView(sourceId, "WH-1", "Main", true));
        api.tasks.add(
                task(
                        UUID.randomUUID(),
                        sourceId,
                        destId,
                        "TR-W",
                        WarehouseTaskKind.TRANSFER_PREPARATION,
                        WarehouseTaskState.IN_WORK,
                        worker));
        viewModel.onScreenOpened();
        assertEquals("В работе · " + worker, viewModel.taskRows().get(0).workerDisplay());
    }

    private PreparationFixture openPreparation() {
        auth = transferAuth();
        viewModel = new WarehouseWorkspaceViewModel(api, auth, Runnable::run, Runnable::run);
        PreparationFixture fx = new PreparationFixture();
        stubPreparation(fx);
        viewModel.onScreenOpened();
        viewModel.selectTask(viewModel.taskRows().get(0));
        return fx;
    }

    private void stubPreparation(PreparationFixture fx) {
        api.warehouses.add(new WarehouseView(fx.sourceId, "SRC", "Source", true));
        api.materials.add(
                new MaterialReferenceView(fx.materialId, "ART-P", "Profile", "", "", "шт"));
        api.cellsByWarehouse.put(
                fx.sourceId,
                List.of(
                        new StorageCellView(fx.cellA, fx.sourceId, "A-01", true),
                        new StorageCellView(fx.cellB, fx.sourceId, "A-02", true)));
        api.documents.put(
                fx.documentId,
                document(
                        fx.documentId,
                        fx.sourceId,
                        fx.destId,
                        List.of(new TransferDocumentLineView(fx.lineId, fx.materialId, new BigDecimal("10"), 1)),
                        3L,
                        5L,
                        null));
        api.suggestions.put(
                fx.documentId,
                List.of(
                        new TransferDocumentSourceSuggestionLine(
                                fx.lineId,
                                fx.materialId,
                                new BigDecimal("10"),
                                List.of(
                                        new SourceCellSuggestion(
                                                fx.cellA,
                                                "A-01",
                                                new BigDecimal("20"),
                                                new BigDecimal("10"))))));
        api.tasks.add(
                task(
                        fx.documentId,
                        fx.sourceId,
                        fx.destId,
                        "TR-PREP",
                        WarehouseTaskKind.TRANSFER_PREPARATION,
                        WarehouseTaskState.IN_WORK,
                        UUID.randomUUID()));
    }

    private static void awaitCondition(java.util.function.BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), "condition not met within " + timeoutMs + "ms");
    }

    private ReceiptFixture openReceipt() {
        auth = transferAuth();
        viewModel = new WarehouseWorkspaceViewModel(api, auth, Runnable::run, Runnable::run);
        ReceiptFixture fx = new ReceiptFixture();
        api.warehouses.add(new WarehouseView(fx.destId, "DST", "Dest", true));
        api.materials.add(
                new MaterialReferenceView(fx.materialId, "ART-R", "Panel", "", "", "шт"));
        api.cellsByWarehouse.put(
                fx.destId,
                List.of(
                        new StorageCellView(fx.destCell, fx.destId, "D-01", true),
                        new StorageCellView(fx.destCellB, fx.destId, "D-02", true)));
        api.documents.put(
                fx.documentId,
                document(
                        fx.documentId,
                        fx.sourceId,
                        fx.destId,
                        List.of(new TransferDocumentLineView(fx.lineId, fx.materialId, new BigDecimal("8"), 1)),
                        2L,
                        4L,
                        11L));
        api.tasks.add(
                task(
                        fx.documentId,
                        fx.sourceId,
                        fx.destId,
                        "TR-RCV",
                        WarehouseTaskKind.TRANSFER_RECEIPT,
                        WarehouseTaskState.IN_WORK,
                        UUID.randomUUID()));
        viewModel.onScreenOpened();
        viewModel.selectTask(viewModel.taskRows().get(0));
        return fx;
    }

    private ReturnFixture openReturn() {
        auth = transferAuth();
        viewModel = new WarehouseWorkspaceViewModel(api, auth, Runnable::run, Runnable::run);
        ReturnFixture fx = new ReturnFixture();
        api.warehouses.add(new WarehouseView(fx.sourceId, "SRC", "Source", true));
        api.materials.add(
                new MaterialReferenceView(fx.materialId, "ART-T", "Tube", "", "", "шт"));
        api.cellsByWarehouse.put(
                fx.sourceId,
                List.of(
                        new StorageCellView(fx.defaultCell, fx.sourceId, "S-01", true),
                        new StorageCellView(fx.altCell, fx.sourceId, "S-02", true)));
        api.documents.put(
                fx.documentId,
                document(
                        fx.documentId,
                        fx.sourceId,
                        fx.destId,
                        List.of(new TransferDocumentLineView(fx.lineId, fx.materialId, new BigDecimal("6"), 1)),
                        1L,
                        2L,
                        21L));
        api.returnPlans.put(
                fx.documentId,
                List.of(
                        new TransferDocumentReturnPlanItem(
                                fx.lineId,
                                fx.materialId,
                                new BigDecimal("6"),
                                fx.defaultCell,
                                "S-01")));
        api.tasks.add(
                task(
                        fx.documentId,
                        fx.sourceId,
                        fx.destId,
                        "TR-RET",
                        WarehouseTaskKind.RETURN_MATERIALS,
                        WarehouseTaskState.IN_WORK,
                        UUID.randomUUID()));
        viewModel.onScreenOpened();
        viewModel.selectTask(viewModel.taskRows().get(0));
        return fx;
    }

    private FakeAuthorization transferAuth() {
        return new FakeAuthorization(
                Set.of(
                        UiShellScreens.WAREHOUSE_VIEW_PERMISSION,
                        UiShellScreens.WAREHOUSE_TRANSFER_PERMISSION));
    }

    private static StorageCellChoice choice(
            java.util.Collection<StorageCellChoice> choices, UUID id) {
        return choices.stream().filter(c -> id.equals(c.id())).findFirst().orElseThrow();
    }

    private static TransferDocumentView document(
            UUID documentId,
            UUID sourceId,
            UUID destId,
            List<TransferDocumentLineView> lines,
            long documentVersion,
            long payloadRevision,
            Long operationalRevision) {
        return new TransferDocumentView(
                documentId,
                "DOC",
                "Transfer",
                "DRAFT",
                documentVersion,
                sourceId,
                destId,
                1,
                payloadRevision,
                lines,
                null,
                null,
                null,
                operationalRevision,
                null,
                null);
    }

    private static WarehouseStockPage emptyPage() {
        return page(List.of(), 0, 0);
    }

    private static WarehouseStockPage page(
            List<WarehouseStockSummaryView> content, int pageIndex, long total) {
        return WarehouseStockPage.of(content, pageIndex, WarehouseWorkspaceViewModel.PAGE_SIZE, total);
    }

    private static WarehouseApi.WarehouseHistoryPage emptyHistoryPage() {
        return historyPage(List.of(), 0, 0);
    }

    private static WarehouseApi.WarehouseHistoryPage historyPage(
            List<WarehouseApi.WarehouseHistoryEntryView> content, int pageIndex, long total) {
        return WarehouseApi.WarehouseHistoryPage.of(
                content, pageIndex, WarehouseWorkspaceViewModel.HISTORY_PAGE_SIZE, total);
    }

    private static WarehouseApi.WarehouseHistoryEntryView historyEntry(
            UUID warehouseId, String operationType, BigDecimal quantity) {
        String display =
                switch (operationType) {
                    case "RECEIPT" -> "Приход";
                    case "MOVE" -> "Перемещение";
                    case "TRANSFER_SEND" -> "Передача";
                    case "TRANSFER_RECEIVE" -> "Приёмка";
                    case "TRANSFER_RETURN" -> "Возврат";
                    case "CONSUMPTION" -> "Списание";
                    case "ADJUSTMENT" -> "Корректировка";
                    default -> operationType;
                };
        return new WarehouseApi.WarehouseHistoryEntryView(
                UUID.randomUUID(),
                Instant.parse("2026-09-10T08:00:00Z"),
                operationType,
                display,
                UUID.randomUUID(),
                "ART",
                "Material",
                "шт",
                quantity,
                warehouseId,
                "Main",
                UUID.randomUUID(),
                "A-01",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
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

    private static WarehouseTaskView task(UUID sourceId, UUID destId, String number) {
        return task(
                UUID.randomUUID(),
                sourceId,
                destId,
                number,
                WarehouseTaskKind.TRANSFER_PREPARATION,
                WarehouseTaskState.NEW,
                null);
    }

    private static WarehouseTaskView task(
            UUID documentId,
            UUID sourceId,
            UUID destId,
            String number,
            WarehouseTaskKind kind,
            WarehouseTaskState state,
            UUID workingUserId) {
        return new WarehouseTaskView(
                documentId,
                number,
                kind,
                state,
                sourceId,
                "SRC",
                "Source",
                destId,
                "DST",
                "Dest",
                2,
                workingUserId,
                workingUserId == null ? null : Instant.EPOCH,
                Instant.EPOCH,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static final class PreparationFixture {
        final UUID documentId = UUID.randomUUID();
        final UUID sourceId = UUID.randomUUID();
        final UUID destId = UUID.randomUUID();
        final UUID lineId = UUID.randomUUID();
        final UUID materialId = UUID.randomUUID();
        final UUID cellA = UUID.randomUUID();
        final UUID cellB = UUID.randomUUID();
    }

    private static final class ReceiptFixture {
        final UUID documentId = UUID.randomUUID();
        final UUID sourceId = UUID.randomUUID();
        final UUID destId = UUID.randomUUID();
        final UUID lineId = UUID.randomUUID();
        final UUID materialId = UUID.randomUUID();
        final UUID destCell = UUID.randomUUID();
        final UUID destCellB = UUID.randomUUID();
    }

    private static final class ReturnFixture {
        final UUID documentId = UUID.randomUUID();
        final UUID sourceId = UUID.randomUUID();
        final UUID destId = UUID.randomUUID();
        final UUID lineId = UUID.randomUUID();
        final UUID materialId = UUID.randomUUID();
        final UUID defaultCell = UUID.randomUUID();
        final UUID altCell = UUID.randomUUID();
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
        private final List<WarehouseApi.WarehouseHistoryPage> historyPages = new ArrayList<>();
        private final AtomicInteger historyPageCursor = new AtomicInteger();
        private final Map<String, WarehouseMaterialStockDetailsView> breakdowns = new HashMap<>();
        private final List<StockSummaryCall> listStockSummariesCalls = new CopyOnWriteArrayList<>();
        private final List<HistoryCall> listHistoryCalls = new CopyOnWriteArrayList<>();
        private final List<BreakdownCall> getStockCellBreakdownCalls = new CopyOnWriteArrayList<>();
        private final List<WarehouseTaskView> tasks = new ArrayList<>();
        private final List<TaskListCall> listMyWarehouseTasksCalls = new CopyOnWriteArrayList<>();
        private final List<UUID> takeTransferTaskInWorkCalls = new CopyOnWriteArrayList<>();
        private final Map<UUID, TransferDocumentView> documents = new HashMap<>();
        private final Map<UUID, List<TransferDocumentSourceSuggestionLine>> suggestions = new HashMap<>();
        private final Map<UUID, List<TransferDocumentReturnPlanItem>> returnPlans = new HashMap<>();
        private final Map<UUID, List<StorageCellView>> cellsByWarehouse = new HashMap<>();
        private final List<MaterialReferenceView> materials = new ArrayList<>();
        private final List<SendTransferDocumentCommand> sendCommands = new CopyOnWriteArrayList<>();
        private final List<ReceiveTransferDocumentCommand> receiveCommands = new CopyOnWriteArrayList<>();
        private final List<RejectTransferDocumentCommand> rejectCommands = new CopyOnWriteArrayList<>();
        private final List<ReturnTransferMaterialsCommand> returnCommands = new CopyOnWriteArrayList<>();
        private final List<UUID> suggestCalls = new CopyOnWriteArrayList<>();
        int listMyWarehousesCalls;
        int listStorageCellsCalls;
        int executeCalls;
        int createWarehouseCalls;
        boolean denyNextStock;
        boolean denyNextHistory;
        long stockDelayMs;
        long taskDelayMs;
        long historyDelayMs;
        UUID sendResultContinuationId;
        RuntimeException sendThrows;
        RuntimeException returnThrows;
        BlockingCallback sendBlock;

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
                sleep(stockDelayMs);
            }
            listStockSummariesCalls.add(new StockSummaryCall(warehouseId, search, pageIndex, pageSize));
            index = Math.min(index, Math.max(stockPages.size() - 1, 0));
            if (stockPages.isEmpty()) {
                return WarehouseStockPage.of(List.of(), pageIndex, pageSize, 0);
            }
            return stockPages.get(index);
        }

        @Override
        public WarehouseApi.WarehouseHistoryPage listHistory(
                UUID warehouseId,
                WarehouseApi.WarehouseHistoryFilter filter,
                int pageIndex,
                int pageSize) {
            if (denyNextHistory) {
                denyNextHistory = false;
                throw new AccessDeniedException("denied");
            }
            int index = historyPageCursor.getAndIncrement();
            if (index == 0 && historyDelayMs > 0) {
                sleep(historyDelayMs);
            }
            listHistoryCalls.add(new HistoryCall(warehouseId, filter, pageIndex, pageSize));
            index = Math.min(index, Math.max(historyPages.size() - 1, 0));
            if (historyPages.isEmpty()) {
                return WarehouseApi.WarehouseHistoryPage.of(List.of(), pageIndex, pageSize, 0);
            }
            return historyPages.get(index);
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
        public List<WarehouseTaskView> listMyWarehouseTasks(UUID warehouseId) {
            if (taskDelayMs > 0) {
                sleep(taskDelayMs);
            }
            listMyWarehouseTasksCalls.add(new TaskListCall(warehouseId));
            if (warehouseId == null) {
                return List.copyOf(tasks);
            }
            return tasks.stream()
                    .filter(
                            t ->
                                    warehouseId.equals(t.sourceWarehouseId())
                                            || warehouseId.equals(t.destinationWarehouseId()))
                    .toList();
        }

        @Override
        public WarehouseTaskView takeTransferTaskInWork(UUID documentId) {
            takeTransferTaskInWorkCalls.add(documentId);
            return tasks.stream()
                    .filter(t -> documentId.equals(t.documentId()))
                    .findFirst()
                    .orElseThrow();
        }

        @Override
        public TransferDocumentView getTransferDocument(UUID documentId) {
            TransferDocumentView document = documents.get(documentId);
            if (document == null) {
                throw new IllegalStateException("document not stubbed");
            }
            return document;
        }

        @Override
        public List<TransferDocumentSourceSuggestionLine> suggestTransferDocumentSourceAllocations(
                UUID documentId) {
            suggestCalls.add(documentId);
            return suggestions.getOrDefault(documentId, List.of());
        }

        @Override
        public List<TransferDocumentReturnPlanItem> listTransferDocumentReturnPlan(UUID documentId) {
            return returnPlans.getOrDefault(documentId, List.of());
        }

        @Override
        public List<StorageCellView> listStorageCells(UUID warehouseId) {
            listStorageCellsCalls++;
            return cellsByWarehouse.getOrDefault(warehouseId, List.of());
        }

        @Override
        public List<MaterialReferenceView> listMaterialReferences() {
            return List.copyOf(materials);
        }

        @Override
        public TransferDocumentSendResult sendTransferDocument(SendTransferDocumentCommand command) {
            if (sendBlock != null) {
                try {
                    sendBlock.run();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }
            sendCommands.add(command);
            if (sendThrows != null) {
                throw sendThrows;
            }
            return new TransferDocumentSendResult(
                    command.documentId(),
                    "POSTED",
                    command.expectedDocumentVersion() + 1,
                    command.expectedPayloadRevision() + 1,
                    List.of(UUID.randomUUID()),
                    sendResultContinuationId);
        }

        @Override
        public TransferDocumentReceiveResult receiveTransferDocument(
                ReceiveTransferDocumentCommand command) {
            receiveCommands.add(command);
            return new TransferDocumentReceiveResult(
                    command.documentId(),
                    "COMPLETED",
                    3L,
                    "SETTLED",
                    "ACCEPTED",
                    command.expectedOperationalRevision() + 1,
                    List.of(UUID.randomUUID()),
                    null);
        }

        @Override
        public TransferDocumentRejectResult rejectTransferDocument(
                RejectTransferDocumentCommand command) {
            rejectCommands.add(command);
            return new TransferDocumentRejectResult(
                    command.documentId(),
                    "REJECTED",
                    "RETURN_PENDING",
                    "REJECTED",
                    command.expectedOperationalRevision() + 1,
                    command.rejectionReason());
        }

        @Override
        public TransferDocumentReturnResult returnTransferMaterials(
                ReturnTransferMaterialsCommand command) {
            returnCommands.add(command);
            if (returnThrows != null) {
                throw returnThrows;
            }
            return new TransferDocumentReturnResult(
                    command.documentId(),
                    "COMPLETED",
                    "SETTLED",
                    "RETURNED",
                    command.expectedOperationalRevision() + 1,
                    List.of(UUID.randomUUID()));
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

        private static void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        @FunctionalInterface
        private interface BlockingCallback {
            void run() throws InterruptedException;
        }

        private record StockSummaryCall(UUID warehouseId, String search, int pageIndex, int pageSize) {}

        private record HistoryCall(
                UUID warehouseId,
                WarehouseApi.WarehouseHistoryFilter filter,
                int pageIndex,
                int pageSize) {}

        private record BreakdownCall(UUID warehouseId, UUID materialReferenceId) {}

        private record TaskListCall(UUID warehouseId) {}
    }
}
