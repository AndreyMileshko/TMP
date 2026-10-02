package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.AllowAllAuthorization;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubApplicationApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubAuthentication;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubOrderQuery;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubQueryApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubWorklistQuery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductionWorkbenchViewModelTest {

    private static final UUID ORDER_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ITEM_A =
            UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID ITEM_B =
            UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2");
    private static final UUID SPEC_ID =
            UUID.fromString("33333333-3333-4333-8333-333333333333");

    private StubQueryApi queryApi;
    private StubApplicationApi applicationApi;
    private StubOrderQuery orderQuery;
    private StubWorklistQuery worklistQuery;
    private AllowAllAuthorization auth;
    private StubAuthentication authentication;
    private ProductionWorkbenchViewModel viewModel;

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @BeforeEach
    void setUp() {
        queryApi = new StubQueryApi();
        applicationApi = new StubApplicationApi();
        orderQuery = new StubOrderQuery();
        worklistQuery = new StubWorklistQuery();
        auth = new AllowAllAuthorization();
        authentication = new StubAuthentication();
        viewModel = newViewModel(auth);
    }

    @Test
    void openOrderCardShowsHeaderFieldsAndPositions() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);

        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertTrue(viewModel.detailVisibleProperty().get());
        assertFalse(viewModel.treeVisibleProperty().get());
        assertEquals("ORD-1", viewModel.orderNumberProperty().get());
        assertEquals("ЗАКАЗ №ORD-1", viewModel.orderTitleProperty().get());
        assertEquals("Клиент", viewModel.customerLabelProperty().get());
        assertEquals("В производстве", viewModel.statusLabelProperty().get());
        assertEquals("0 из 20", viewModel.progressLabelProperty().get());
        assertEquals(2, viewModel.itemRows().size());
        assertEquals("Поз. 1", viewModel.itemRows().get(0).positionLabel());
        assertEquals("P-1 — Изделие", viewModel.itemRows().get(0).productLabel());
    }

    @Test
    void quantityModeDefaultsToStandard() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);

        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertEquals(QuantityModeView.STANDARD, viewModel.selectedQuantityModeProperty().get());
        assertEquals(QuantityModeView.STANDARD, viewModel.savedQuantityMode());
        assertFalse(viewModel.quantityModeDirtyProperty().get());
    }

    @Test
    void changingModeMarksDirtyAndSaveCallsApi() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        viewModel.openForOrder(OrderId.of(ORDER_ID));

        viewModel.selectQuantityMode(QuantityModeView.FLEXIBLE);
        assertTrue(viewModel.quantityModeDirtyProperty().get());

        viewModel.saveQuantityMode();

        assertEquals(QuantityModeView.FLEXIBLE, viewModel.savedQuantityMode());
        assertFalse(viewModel.quantityModeDirtyProperty().get());
        assertEquals(1L, viewModel.quantityModeVersion());
        assertEquals(
                QuantityModeView.FLEXIBLE,
                applicationApi.getOrderQuantityMode(ORDER_ID).quantityMode());
    }

    @Test
    void saveQuantityModeNoOpWhenUnchanged() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        viewModel.openForOrder(OrderId.of(ORDER_ID));

        viewModel.saveQuantityMode();

        assertEquals(QuantityModeView.STANDARD, viewModel.savedQuantityMode());
        assertFalse(viewModel.quantityModeDirtyProperty().get());
    }

    @Test
    void quantityModeOptimisticConflictShowsMessageAndReloads() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        viewModel.openForOrder(OrderId.of(ORDER_ID));
        viewModel.selectQuantityMode(QuantityModeView.FLEXIBLE);
        applicationApi.changeOrderQuantityModeFailure =
                new ProductionWorkbenchUiTestSupport.OrderQuantityModeOptimisticLockStubException(
                        ORDER_ID, 0L);
        applicationApi.putOrderQuantityMode(ORDER_ID, QuantityModeView.FLEXIBLE, 3L);

        viewModel.saveQuantityMode();

        assertEquals(
                ProductionUiErrorMapper.QUANTITY_MODE_CONFLICT,
                viewModel.errorMessageProperty().get());
        assertEquals(QuantityModeView.FLEXIBLE, viewModel.savedQuantityMode());
        assertEquals(3L, viewModel.quantityModeVersion());
    }

    @Test
    void acceptWholeOrderDelegatesToApplicationApi() {
        seedDetailOrder(OrderProductionViewStatus.NOT_ACCEPTED);
        viewModel.openForOrder(OrderId.of(ORDER_ID));
        assertTrue(viewModel.canAcceptProperty().get());

        viewModel.acceptOrder();

        assertEquals(List.of(ORDER_ID), applicationApi.acceptCalls);
        assertEquals(List.of("tester"), applicationApi.acceptActors);
    }

    @Test
    void viewOnlyPermissionSeesQuantityModeReadonly() {
        auth = new AllowAllAuthorization(Set.of(UiShellScreens.PRODUCTION_VIEW_PERMISSION));
        viewModel = newViewModel(auth);
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);

        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertFalse(viewModel.canEditQuantityModeProperty().get());
        viewModel.selectQuantityMode(QuantityModeView.FLEXIBLE);
        assertEquals(QuantityModeView.STANDARD, viewModel.selectedQuantityModeProperty().get());
    }

    @Test
    void acceptPermissionCanEditQuantityMode() {
        auth =
                new AllowAllAuthorization(
                        Set.of(
                                UiShellScreens.PRODUCTION_VIEW_PERMISSION,
                                UiShellScreens.PRODUCTION_ACCEPT_PERMISSION));
        viewModel = newViewModel(auth);
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);

        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertTrue(viewModel.canEditQuantityModeProperty().get());
        viewModel.selectQuantityMode(QuantityModeView.FLEXIBLE);
        assertEquals(QuantityModeView.FLEXIBLE, viewModel.selectedQuantityModeProperty().get());
    }

    @Test
    void transferPermissionAloneCannotEditQuantityMode() {
        auth =
                new AllowAllAuthorization(
                        Set.of(
                                UiShellScreens.PRODUCTION_VIEW_PERMISSION,
                                UiShellScreens.PRODUCTION_TRANSFER_PERMISSION));
        viewModel = newViewModel(auth);
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);

        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertFalse(viewModel.canEditQuantityModeProperty().get());
    }

    @Test
    void releasePermissionAloneCannotEditQuantityMode() {
        auth =
                new AllowAllAuthorization(
                        Set.of(
                                UiShellScreens.PRODUCTION_VIEW_PERMISSION,
                                UiShellScreens.PRODUCTION_RELEASE_PERMISSION));
        viewModel = newViewModel(auth);
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);

        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertFalse(viewModel.canEditQuantityModeProperty().get());
    }

    @Test
    void quantityModeEditableIndependentOfProductionState() {
        for (OrderProductionViewStatus status :
                List.of(
                        OrderProductionViewStatus.NOT_ACCEPTED,
                        OrderProductionViewStatus.IN_PRODUCTION,
                        OrderProductionViewStatus.MANUFACTURED,
                        OrderProductionViewStatus.CANCELLED)) {
            seedDetailOrder(status);
            viewModel.openForOrder(OrderId.of(ORDER_ID));
            assertTrue(
                    viewModel.canEditQuantityModeProperty().get(),
                    "mode should be editable for " + status);
            viewModel.selectQuantityMode(QuantityModeView.FLEXIBLE);
            assertTrue(viewModel.quantityModeDirtyProperty().get());
            viewModel.backToTree();
            applicationApi.putOrderQuantityMode(ORDER_ID, QuantityModeView.STANDARD, 0L);
        }
    }

    @Test
    void persistedFlexibleModeIsShown() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        applicationApi.putOrderQuantityMode(ORDER_ID, QuantityModeView.FLEXIBLE, 2L);

        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertEquals(QuantityModeView.FLEXIBLE, viewModel.selectedQuantityModeProperty().get());
        assertEquals(2L, viewModel.quantityModeVersion());
        assertTrue(viewModel.quantityModeHintProperty().get().contains("вручную"));
    }

    @Test
    void flexibleToStandardSaveSucceeds() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        applicationApi.putOrderQuantityMode(ORDER_ID, QuantityModeView.FLEXIBLE, 1L);
        viewModel.openForOrder(OrderId.of(ORDER_ID));

        viewModel.selectQuantityMode(QuantityModeView.STANDARD);
        viewModel.saveQuantityMode();

        assertEquals(QuantityModeView.STANDARD, viewModel.savedQuantityMode());
        assertEquals(2L, viewModel.quantityModeVersion());
        assertFalse(viewModel.quantityModeDirtyProperty().get());
    }

    @Test
    void siteMissingShowsDash() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        viewModel.openForOrder(OrderId.of(ORDER_ID));
        assertEquals("—", viewModel.siteLabelProperty().get());
    }

    @Test
    void backToTreeRetainsSelectionFilterAndExpanded() {
        seedTreeOrder();
        viewModel.loadTree();
        viewModel.selectOrder(ORDER_ID);
        viewModel.setOrderExpanded(ORDER_ID, true);
        viewModel.searchTextProperty().set("ORD");
        viewModel.statusFilterProperty().set(ProductionTreeStatusFilter.IN_PROGRESS);

        viewModel.openOrderDetail(OrderId.of(ORDER_ID));
        viewModel.backToTree();

        assertTrue(viewModel.treeVisibleProperty().get());
        assertFalse(viewModel.detailVisibleProperty().get());
        assertEquals(2, viewModel.selectedOrderItemRefs().size());
        assertTrue(viewModel.expandedOrderIds().contains(ORDER_ID));
        assertEquals("ORD", viewModel.searchTextProperty().get());
        assertEquals(ProductionTreeStatusFilter.IN_PROGRESS, viewModel.statusFilterProperty().get());
    }

    @Test
    void itemRowsDoNotExposeUuidInPresentationLabels() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        viewModel.openForOrder(OrderId.of(ORDER_ID));

        for (ProductionItemRow row : viewModel.itemRows()) {
            assertFalse(row.positionLabel().contains(ITEM_A.toString()));
            assertFalse(row.positionLabel().contains(ITEM_B.toString()));
            assertFalse(row.productLabel().contains(ITEM_A.toString()));
            assertFalse(row.productLabel().contains(ITEM_B.toString()));
        }
    }

    @Test
    void loadsAllItemPagesIntoOrderCard() {
        worklistQuery.rows.add(
                ProductionWorkbenchUiTestSupport.worklistRow(ORDER_ID, "ORD-BIG", "Клиент"));
        orderQuery.orders.put(
                OrderId.of(ORDER_ID), ProductionWorkbenchUiTestSupport.order(ORDER_ID, "ORD-BIG"));
        queryApi.listFacts.put(
                ORDER_ID,
                ProductionWorkbenchUiTestSupport.productionListFacts(
                        ORDER_ID, OrderProductionViewStatus.IN_PRODUCTION, 101, 0, 101));
        for (int i = 0; i < 101; i++) {
            UUID itemId = UUID.nameUUIDFromBytes(("card-item-" + i).getBytes());
            orderQuery.items.add(
                    ProductionWorkbenchUiTestSupport.item(ORDER_ID, itemId, String.valueOf(i + 1)));
        }
        queryApi.view =
                new OrderProductionView(
                        ORDER_ID, OrderProductionViewStatus.IN_PRODUCTION, 101, 101, 0, 0, 0);

        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertEquals(101, viewModel.itemRows().size());
    }

    private ProductionWorkbenchViewModel newViewModel(AllowAllAuthorization authorization) {
        return new ProductionWorkbenchViewModel(
                queryApi,
                applicationApi,
                orderQuery,
                worklistQuery,
                new ProductionWorkbenchUiTestSupport.StubWarehouseApi(),
                authorization,
                authentication);
    }

    private void seedTreeOrder() {
        worklistQuery.rows.add(
                ProductionWorkbenchUiTestSupport.worklistRow(ORDER_ID, "ORD-1", "Клиент"));
        orderQuery.orders.put(
                OrderId.of(ORDER_ID), ProductionWorkbenchUiTestSupport.order(ORDER_ID, "ORD-1"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_ID, ITEM_A, "1"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_ID, ITEM_B, "2"));
        queryApi.listFacts.put(
                ORDER_ID,
                ProductionWorkbenchUiTestSupport.productionListFacts(
                        ORDER_ID, OrderProductionViewStatus.IN_PRODUCTION, 20, 0, 20));
    }

    @Test
    void cancelAvailableInProductionWithPermission() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertTrue(viewModel.canCancelProperty().get());
        assertEquals(20L, viewModel.currentRemainingQuantity());
        assertEquals(0L, viewModel.currentReleasedQuantity());
    }

    @Test
    void cancelHiddenWithoutPermission() {
        auth =
                new AllowAllAuthorization(
                        Set.of(
                                UiShellScreens.PRODUCTION_VIEW_PERMISSION,
                                UiShellScreens.PRODUCTION_ACCEPT_PERMISSION));
        viewModel = newViewModel(auth);
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertFalse(viewModel.canCancelProperty().get());
    }

    @Test
    void cancelUnavailableForManufacturedAndCancelled() {
        seedDetailOrder(OrderProductionViewStatus.MANUFACTURED);
        viewModel.openForOrder(OrderId.of(ORDER_ID));
        assertFalse(viewModel.canCancelProperty().get());

        seedDetailOrder(OrderProductionViewStatus.CANCELLED);
        viewModel.openForOrder(OrderId.of(ORDER_ID));
        assertFalse(viewModel.canCancelProperty().get());
    }

    @Test
    void cancelUnavailableForNotAccepted() {
        seedDetailOrder(OrderProductionViewStatus.NOT_ACCEPTED);
        viewModel.openForOrder(OrderId.of(ORDER_ID));
        assertFalse(viewModel.canCancelProperty().get());
    }

    @Test
    void cancelSuccessCallsApiAndRefreshes() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        viewModel.openForOrder(OrderId.of(ORDER_ID));
        queryApi.view =
                new OrderProductionView(
                        ORDER_ID, OrderProductionViewStatus.CANCELLED, 2, 20, 0, 0, 0);

        viewModel.cancelOrderProduction(Optional.of("тест"));

        assertEquals(List.of(ORDER_ID), applicationApi.cancelCalls);
        assertEquals(List.of(Optional.of("тест")), applicationApi.cancelReasons);
        assertEquals(
                ProductionUiErrorMapper.cancelSuccess("ORD-1"),
                viewModel.statusMessageProperty().get());
        assertEquals("Отменён", viewModel.statusLabelProperty().get());
        assertFalse(viewModel.canCancelProperty().get());
    }

    @Test
    void cancelConflictShowsMessageAndRefreshes() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        viewModel.openForOrder(OrderId.of(ORDER_ID));
        applicationApi.cancelFailure =
                new RuntimeException("ProductionCancellationAlreadyExistsException");
        queryApi.view =
                new OrderProductionView(
                        ORDER_ID, OrderProductionViewStatus.CANCELLED, 2, 20, 0, 0, 0);

        viewModel.cancelOrderProduction(Optional.empty());

        assertEquals(
                ProductionUiErrorMapper.CANCEL_CONFLICT, viewModel.errorMessageProperty().get());
        assertEquals("Отменён", viewModel.statusLabelProperty().get());
    }

    @Test
    void historyEmptyStateWhenNoEntries() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertTrue(viewModel.historyEmptyProperty().get());
        assertFalse(viewModel.historyDetailsVisibleProperty().get());
        assertTrue(viewModel.historyRows().isEmpty());
    }

    @Test
    void historyShowsLatestHumanReadableWithoutRawSummary() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        Instant older = Instant.parse("2026-10-02T07:00:00Z");
        Instant newer = Instant.parse("2026-10-02T07:05:00Z");
        queryApi.history.add(
                new com.tmp.production.api.ProductionQueryApi.ProductionHistoryEntryView(
                        UUID.randomUUID(),
                        ORDER_ID,
                        com.tmp.production.api.ProductionQueryApi.ProductionHistoryType
                                .ORDER_ACCEPTED,
                        older,
                        older,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of("tester"),
                        Optional.of("Order accepted into production")));
        queryApi.history.add(
                new com.tmp.production.api.ProductionQueryApi.ProductionHistoryEntryView(
                        UUID.randomUUID(),
                        ORDER_ID,
                        com.tmp.production.api.ProductionQueryApi.ProductionHistoryType
                                .PRODUCTS_RELEASED,
                        newer,
                        newer,
                        Optional.empty(),
                        Optional.of(UUID.randomUUID()),
                        Optional.of(UUID.randomUUID()),
                        Optional.empty(),
                        Optional.of("Products released")));

        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertFalse(viewModel.historyEmptyProperty().get());
        assertTrue(viewModel.historyDetailsVisibleProperty().get());
        assertEquals("Выпуск изделий", viewModel.historyLatestOperationProperty().get());
        assertEquals(2, viewModel.historyRows().size());
        assertEquals("Выпуск изделий", viewModel.historyRows().get(0).operationLabel());
        assertEquals("—", viewModel.historyRows().get(0).actorLabel());
        assertEquals("tester", viewModel.historyRows().get(1).actorLabel());
        assertFalse(viewModel.historyLatestOperationProperty().get().contains("Products"));
        assertFalse(viewModel.historyRows().get(0).descriptionLabel().contains("UUID"));
    }

    @Test
    void historyActorUuidFallbackIsDash() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        Instant at = Instant.parse("2026-10-02T07:05:00Z");
        queryApi.history.add(
                new com.tmp.production.api.ProductionQueryApi.ProductionHistoryEntryView(
                        UUID.randomUUID(),
                        ORDER_ID,
                        com.tmp.production.api.ProductionQueryApi.ProductionHistoryType
                                .ORDER_ACCEPTED,
                        at,
                        at,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of("11111111-1111-4111-8111-111111111111"),
                        Optional.of("Order accepted into production")));

        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertEquals("—", viewModel.historyRows().get(0).actorLabel());
    }

    private void seedDetailOrder(OrderProductionViewStatus status) {
        seedTreeOrder();
        queryApi.view = new OrderProductionView(ORDER_ID, status, 2, 20, 0, 0, 0);
        queryApi.statesByOrder.put(ORDER_ID, itemStates());
    }

    private java.util.Map<UUID, ItemProductionStateView> itemStates() {
        return java.util.Map.of(
                ITEM_A,
                new ItemProductionStateView(
                        ORDER_ID,
                        ITEM_A,
                        SPEC_ID,
                        ItemProductionStateStatus.IN_PRODUCTION,
                        10,
                        10,
                        10,
                        0,
                        Optional.empty(),
                        Instant.parse("2026-01-01T10:00:00Z"),
                        List.of()),
                ITEM_B,
                new ItemProductionStateView(
                        ORDER_ID,
                        ITEM_B,
                        SPEC_ID,
                        ItemProductionStateStatus.IN_PRODUCTION,
                        10,
                        10,
                        10,
                        0,
                        Optional.empty(),
                        Instant.parse("2026-01-01T10:00:00Z"),
                        List.of()));
    }
}
