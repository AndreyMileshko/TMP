package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.AllowAllAuthorization;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubApplicationApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubAuthentication;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubOrderQuery;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubQueryApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubWorklistQuery;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchViewModel.TreeOrderModel;
import com.tmp.ui.shell.screen.production.ProductionTreeSelectionModel.OrderCheckState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductionWorkbenchTreeViewModelTest {

    private static final UUID ORDER_1 = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORDER_2 = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ITEM_1A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID ITEM_1B = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2");
    private static final UUID ITEM_1C = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3");
    private static final UUID ITEM_2A = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1");
    private static final UUID SPEC = UUID.fromString("33333333-3333-4333-8333-333333333333");

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
        viewModel =
                new ProductionWorkbenchViewModel(
                        queryApi,
                        applicationApi,
                        orderQuery,
                        worklistQuery,
                        new ProductionWorkbenchUiTestSupport.StubWarehouseApi(),
                        auth,
                        authentication);
    }

    @Test
    void oneOrderWithThreeItemsBuildsParentAndThreeChildren() {
        seedSingleOrderThreeItems();

        viewModel.loadTree();

        List<TreeOrderModel> tree = viewModel.visibleTreeProperty().get();
        assertEquals(1, tree.size());
        assertEquals(3, tree.get(0).items().size());
        assertTrue(tree.get(0).orderNode().isOrder());
        assertTrue(tree.get(0).items().stream().allMatch(ProductionTreeNode::isItem));
    }

    @Test
    void twoOrdersHaveCorrectChildrenUnderEachParent() {
        seedTwoOrders();

        viewModel.loadTree();

        List<TreeOrderModel> tree = viewModel.visibleTreeProperty().get();
        assertEquals(2, tree.size());
        assertEquals(1, tree.get(0).items().size());
        assertEquals(3, tree.get(1).items().size());
        assertTrue(tree.get(0).orderNode().identityLabel().contains("ORD-1"));
        assertTrue(tree.get(1).orderNode().identityLabel().contains("ORD-2"));
    }

    @Test
    void treeLabelsNeverExposeQuantityModeTokens() {
        seedSingleOrderThreeItems();
        applicationApi.putOrderQuantityMode(ORDER_1, com.tmp.production.api.ProductionApplicationApi.QuantityModeView.FLEXIBLE, 1L);

        viewModel.loadTree();

        for (TreeOrderModel model : viewModel.visibleTreeProperty().get()) {
            assertTreeTextFreeOfModeTokens(model.orderNode().identityLabel());
            assertTreeTextFreeOfModeTokens(model.orderNode().secondaryLabel());
            assertTreeTextFreeOfModeTokens(model.orderNode().quantityLabel());
            assertTreeTextFreeOfModeTokens(model.orderNode().statusLabel());
            for (ProductionTreeNode item : model.items()) {
                assertTreeTextFreeOfModeTokens(item.identityLabel());
                assertTreeTextFreeOfModeTokens(item.secondaryLabel());
                assertTreeTextFreeOfModeTokens(item.quantityLabel());
                assertTreeTextFreeOfModeTokens(item.statusLabel());
            }
        }
    }

    private static void assertTreeTextFreeOfModeTokens(String value) {
        String text = value == null ? "" : value;
        assertFalse(text.contains("STANDARD"));
        assertFalse(text.contains("FLEXIBLE"));
        assertFalse(text.contains("Режим"));
    }

    @Test
    void treeLabelsDoNotExposeRawUuidText() {
        seedSingleOrderThreeItems();

        viewModel.loadTree();

        for (TreeOrderModel model : viewModel.visibleTreeProperty().get()) {
            assertFalse(model.orderNode().identityLabel().contains(ORDER_1.toString()));
            assertFalse(model.orderNode().secondaryLabel().contains(ORDER_1.toString()));
            for (ProductionTreeNode item : model.items()) {
                UUID itemId = item.sourceOrderItemId().orElseThrow();
                assertFalse(item.identityLabel().contains(itemId.toString()));
                assertFalse(item.identityLabel().contains("-"));
                assertFalse(item.secondaryLabel().contains(itemId.toString()));
            }
        }
    }

    @Test
    void quantityAndStatusLabelsReflectProductionState() {
        seedSingleOrderThreeItems();
        queryApi.putItemState(
                itemState(ITEM_1A, 10, 2, 8, ItemProductionStateStatus.IN_PRODUCTION));

        viewModel.loadTree();

        ProductionTreeNode itemNode = viewModel.visibleTreeProperty().get().get(0).items().get(0);
        assertEquals("10", itemNode.quantityLabel());
        assertEquals("2", itemNode.releasedLabel());
        assertEquals("8", itemNode.remainingLabel());
        assertEquals("В производстве", itemNode.statusLabel());
    }

    @Test
    void missingExternalPositionUsesHumanReadableFallback() {
        worklistQuery.rows.add(ProductionWorkbenchUiTestSupport.worklistRow(ORDER_1, "ORD-1", "Клиент"));
        orderQuery.orders.put(OrderId.of(ORDER_1), ProductionWorkbenchUiTestSupport.order(ORDER_1, "ORD-1"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_1, ITEM_1A));
        queryApi.listFacts.put(
                ORDER_1,
                ProductionWorkbenchUiTestSupport.productionListFacts(
                        ORDER_1, OrderProductionViewStatus.IN_PRODUCTION, 5, 0, 5));

        viewModel.loadTree();

        String label = viewModel.visibleTreeProperty().get().get(0).items().get(0).identityLabel();
        assertEquals("Позиция 1", label);
        assertFalse(label.contains(ITEM_1A.toString()));
    }

    @Test
    void selectOrderChecksAllChildren() {
        seedSingleOrderThreeItems();
        viewModel.loadTree();

        viewModel.selectOrder(ORDER_1);

        assertEquals(3, viewModel.selectedOrderItemRefs().size());
        TreeOrderModel model = viewModel.visibleTreeProperty().get().get(0);
        assertEquals(
                OrderCheckState.CHECKED,
                viewModel
                        .selectionModel()
                        .orderCheckState(ORDER_1, model.orderNode().childRefs()));
    }

    @Test
    void deselectOrderClearsChildren() {
        seedSingleOrderThreeItems();
        viewModel.loadTree();
        viewModel.selectOrder(ORDER_1);

        viewModel.deselectOrder(ORDER_1);

        assertTrue(viewModel.selectedOrderItemRefs().isEmpty());
    }

    @Test
    void selectOneChildMakesOrderIndeterminate() {
        seedSingleOrderThreeItems();
        viewModel.loadTree();

        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_1, ITEM_1B), true);

        assertEquals(
                OrderCheckState.INDETERMINATE,
                viewModel
                        .selectionModel()
                        .orderCheckState(
                                ORDER_1,
                                viewModel.visibleTreeProperty().get().get(0).orderNode().childRefs()));
    }

    @Test
    void selectAllChildrenIndividuallyChecksOrder() {
        seedSingleOrderThreeItems();
        viewModel.loadTree();

        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_1, ITEM_1A), true);
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_1, ITEM_1B), true);
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_1, ITEM_1C), true);

        assertEquals(
                OrderCheckState.CHECKED,
                viewModel
                        .selectionModel()
                        .orderCheckState(
                                ORDER_1,
                                viewModel.visibleTreeProperty().get().get(0).orderNode().childRefs()));
    }

    @Test
    void crossOrderSelectionInViewModel() {
        seedTwoOrders();
        viewModel.loadTree();

        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_1, ITEM_2A), true);
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_2, ITEM_1A), true);

        assertEquals(2, viewModel.selectedOrderItemRefs().size());
    }

    @Test
    void selectedOrderItemRefsAreDeterministic() {
        seedTwoOrders();
        viewModel.loadTree();

        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_2, ITEM_1A), true);
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_2, ITEM_1B), true);
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_1, ITEM_2A), true);

        List<ProductionOrderItemRef> refs = viewModel.selectedOrderItemRefs();
        assertEquals(
                List.of(
                        new ProductionOrderItemRef(ORDER_1, ITEM_2A),
                        new ProductionOrderItemRef(ORDER_2, ITEM_1A),
                        new ProductionOrderItemRef(ORDER_2, ITEM_1B)),
                refs);
    }

    @Test
    void selectionSurvivesSearchFilterHidingSelectedChild() {
        worklistQuery.rows.add(ProductionWorkbenchUiTestSupport.worklistRow(ORDER_1, "ORD-1", "Клиент A"));
        orderQuery.orders.put(OrderId.of(ORDER_1), ProductionWorkbenchUiTestSupport.order(ORDER_1, "ORD-1"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_1, ITEM_1A, "1"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_1, ITEM_1B, "2"));
        orderQuery.items.add(
                com.tmp.order.api.OrderItemDto.of(
                        com.tmp.order.api.OrderItemId.of(ITEM_1C),
                        OrderId.of(ORDER_1),
                        "UNIQ-CODE",
                        "Уникальное имя",
                        null,
                        "3",
                        com.tmp.order.api.OrderItemStatus.ACTIVE,
                        com.tmp.order.api.RevisionNumber.first(),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z")));
        queryApi.listFacts.put(
                ORDER_1,
                ProductionWorkbenchUiTestSupport.productionListFacts(
                        ORDER_1, OrderProductionViewStatus.IN_PRODUCTION, 30, 0, 30));
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_1, ITEM_1C), true);

        viewModel.searchTextProperty().set("UNIQ");
        assertEquals(1, viewModel.visibleTreeProperty().get().get(0).items().size());
        assertEquals(1, viewModel.selectedOrderItemRefs().size());

        viewModel.searchTextProperty().set("");
        assertEquals(1, viewModel.selectedOrderItemRefs().size());
        assertTrue(viewModel.selectedOrderItemRefs().contains(new ProductionOrderItemRef(ORDER_1, ITEM_1C)));
    }

    @Test
    void refreshRetainsSelectionAndExpandedState() {
        seedSingleOrderThreeItems();
        viewModel.loadTree();
        viewModel.selectOrder(ORDER_1);
        viewModel.setOrderExpanded(ORDER_1, true);

        viewModel.loadTree();

        assertEquals(3, viewModel.selectedOrderItemRefs().size());
        assertTrue(viewModel.visibleTreeProperty().get().get(0).expanded());
    }

    @Test
    void reloadPrunesSelectionForRemovedItems() {
        seedSingleOrderThreeItems();
        viewModel.loadTree();
        viewModel.selectOrder(ORDER_1);

        orderQuery.items.removeIf(i -> i.orderItemId().value().equals(ITEM_1C));
        viewModel.loadTree();

        assertEquals(2, viewModel.selectedOrderItemRefs().size());
        assertFalse(
                viewModel
                        .selectedOrderItemRefs()
                        .contains(new ProductionOrderItemRef(ORDER_1, ITEM_1C)));
    }

    @Test
    void orderWithMoreThanMaxPageSizeLoadsAllItemsIntoTree() {
        worklistQuery.rows.add(ProductionWorkbenchUiTestSupport.worklistRow(ORDER_1, "ORD-BIG", "Клиент"));
        orderQuery.orders.put(OrderId.of(ORDER_1), ProductionWorkbenchUiTestSupport.order(ORDER_1, "ORD-BIG"));
        queryApi.listFacts.put(
                ORDER_1,
                ProductionWorkbenchUiTestSupport.productionListFacts(
                        ORDER_1, OrderProductionViewStatus.IN_PRODUCTION, 101, 0, 101));
        for (int i = 0; i < 101; i++) {
            UUID itemId = UUID.nameUUIDFromBytes(("tree-item-" + i).getBytes());
            orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_1, itemId, String.valueOf(i + 1)));
        }

        viewModel.loadTree();

        assertEquals(101, viewModel.visibleTreeProperty().get().get(0).items().size());
    }

    @Test
    void viewOnlyPermissionAllowsTreeLoadAndSelection() {
        auth = new AllowAllAuthorization(Set.of(UiShellScreens.PRODUCTION_VIEW_PERMISSION));
        viewModel =
                new ProductionWorkbenchViewModel(
                        queryApi,
                        applicationApi,
                        orderQuery,
                        worklistQuery,
                        new ProductionWorkbenchUiTestSupport.StubWarehouseApi(),
                        auth,
                        authentication);
        seedSingleOrderThreeItems();

        viewModel.loadTree();
        viewModel.selectOrder(ORDER_1);

        assertEquals(3, viewModel.visibleTreeProperty().get().get(0).items().size());
        assertEquals(3, viewModel.selectedOrderItemRefs().size());
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    private void seedSingleOrderThreeItems() {
        worklistQuery.rows.add(ProductionWorkbenchUiTestSupport.worklistRow(ORDER_1, "ORD-1", "Клиент A"));
        orderQuery.orders.put(OrderId.of(ORDER_1), ProductionWorkbenchUiTestSupport.order(ORDER_1, "ORD-1"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_1, ITEM_1A, "1"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_1, ITEM_1B, "2"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_1, ITEM_1C, "3"));
        queryApi.listFacts.put(
                ORDER_1,
                ProductionWorkbenchUiTestSupport.productionListFacts(
                        ORDER_1, OrderProductionViewStatus.IN_PRODUCTION, 30, 0, 30));
    }

    private void seedTwoOrders() {
        worklistQuery.rows.add(ProductionWorkbenchUiTestSupport.worklistRow(ORDER_1, "ORD-1", "Клиент A"));
        worklistQuery.rows.add(ProductionWorkbenchUiTestSupport.worklistRow(ORDER_2, "ORD-2", "Клиент B"));
        orderQuery.orders.put(OrderId.of(ORDER_1), ProductionWorkbenchUiTestSupport.order(ORDER_1, "ORD-1"));
        orderQuery.orders.put(OrderId.of(ORDER_2), ProductionWorkbenchUiTestSupport.order(ORDER_2, "ORD-2"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_1, ITEM_2A, "1"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_2, ITEM_1A, "1"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_2, ITEM_1B, "2"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_2, ITEM_1C, "3"));
        queryApi.listFacts.put(
                ORDER_1,
                ProductionWorkbenchUiTestSupport.productionListFacts(
                        ORDER_1, OrderProductionViewStatus.IN_PRODUCTION, 10, 0, 10));
        queryApi.listFacts.put(
                ORDER_2,
                ProductionWorkbenchUiTestSupport.productionListFacts(
                        ORDER_2, OrderProductionViewStatus.IN_PRODUCTION, 20, 0, 20));
    }

    private static ItemProductionStateView itemState(
            UUID itemId, long ordered, long released, long active, ItemProductionStateStatus status) {
        return new ItemProductionStateView(
                ORDER_1,
                itemId,
                SPEC,
                status,
                ordered,
                ordered,
                active,
                released,
                Optional.empty(),
                Instant.parse("2026-01-01T10:00:00Z"),
                List.of());
    }
}
