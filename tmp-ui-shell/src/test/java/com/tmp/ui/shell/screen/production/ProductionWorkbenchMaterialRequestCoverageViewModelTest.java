package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementProductCoverageView;
import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.AllowAllAuthorization;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubApplicationApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubAuthentication;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubOrderQuery;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubQueryApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubWarehouseApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubWorklistQuery;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchViewModel.TreeOrderModel;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Focused Workbench presentation tests for authoritative Material Request coverage column values.
 */
class ProductionWorkbenchMaterialRequestCoverageViewModelTest {

    private static final UUID ORDER_1 = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID ITEM_1A = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1");
    private static final UUID ITEM_1B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2");

    private StubQueryApi queryApi;
    private StubApplicationApi applicationApi;
    private StubOrderQuery orderQuery;
    private StubWorklistQuery worklistQuery;
    private ProductionWorkbenchViewModel viewModel;

    @BeforeEach
    void setUp() {
        queryApi = new StubQueryApi();
        applicationApi = new StubApplicationApi();
        orderQuery = new StubOrderQuery();
        worklistQuery = new StubWorklistQuery();
        viewModel =
                new ProductionWorkbenchViewModel(
                        queryApi,
                        applicationApi,
                        orderQuery,
                        worklistQuery,
                        new StubWarehouseApi(),
                        new AllowAllAuthorization(),
                        new StubAuthentication());
    }

    @Test
    void singlePositionShowsAuthoritativeSubmittedOverOrdered() {
        seedOrderWithItems(ORDER_1, "ORD-1", 5, ITEM_1A);
        queryApi.putItemState(
                ProductionWorkbenchUiTestSupport.activeItemState(ORDER_1, ITEM_1A, 5));
        applicationApi.putCoverage(coverage(ORDER_1, ITEM_1A, 5, 5, 0, 3, 3, 2));

        viewModel.loadTree();

        ProductionTreeNode item = singleItem(ORDER_1);
        assertEquals("Учтено: 3/5", item.materialRequestsLabel());
        assertFalse(item.materialRequestsLabel().contains("Повторно"));
    }

    @Test
    void multipleSubmittedRequirementsSumOncePerRequirementNotPerMaterialLine() {
        seedOrderWithItems(ORDER_1, "ORD-1", 5, ITEM_1A);
        queryApi.putItemState(
                ProductionWorkbenchUiTestSupport.activeItemState(ORDER_1, ITEM_1A, 5));
        // Coverage API already exposes summed requested_product_quantity (not demand-line count).
        applicationApi.putCoverage(coverage(ORDER_1, ITEM_1A, 5, 3, 2, 3, 1, 2));

        viewModel.loadTree();

        assertEquals("Учтено: 3/5", singleItem(ORDER_1).materialRequestsLabel());
        assertEquals("Учтено: 3/5", singleOrder(ORDER_1).materialRequestsLabel());
    }

    @Test
    void draftOnlyCoverageIsAbsent() {
        seedOrderWithItems(ORDER_1, "ORD-1", 4, ITEM_1A);
        queryApi.putItemState(
                ProductionWorkbenchUiTestSupport.activeItemState(ORDER_1, ITEM_1A, 4));
        applicationApi.putCoverage(coverage(ORDER_1, ITEM_1A, 4, 4, 0, 0, 0, 4));

        viewModel.loadTree();

        assertEquals("—", singleItem(ORDER_1).materialRequestsLabel());
        assertEquals("—", singleOrder(ORDER_1).materialRequestsLabel());
    }

    @Test
    void parentAggregatesChildCoverageWithoutDoubleCounting() {
        seedOrderWithItems(ORDER_1, "ORD-MULTI", 8, ITEM_1A, ITEM_1B);
        queryApi.putItemState(
                ProductionWorkbenchUiTestSupport.activeItemState(ORDER_1, ITEM_1A, 5));
        queryApi.putItemState(
                ProductionWorkbenchUiTestSupport.activeItemState(ORDER_1, ITEM_1B, 3));
        applicationApi.putCoverage(coverage(ORDER_1, ITEM_1A, 5, 5, 0, 2, 2, 3));
        applicationApi.putCoverage(coverage(ORDER_1, ITEM_1B, 3, 3, 0, 1, 1, 2));

        viewModel.loadTree();

        TreeOrderModel model = viewModel.visibleTreeProperty().get().get(0);
        assertEquals("Учтено: 2/5", model.items().get(0).materialRequestsLabel());
        assertEquals("Учтено: 1/3", model.items().get(1).materialRequestsLabel());
        assertEquals("Учтено: 3/8", model.orderNode().materialRequestsLabel());
    }

    @Test
    void noRequestPresentationIsDash() {
        seedOrderWithItems(ORDER_1, "ORD-1", 2, ITEM_1A);
        queryApi.putItemState(
                ProductionWorkbenchUiTestSupport.activeItemState(ORDER_1, ITEM_1A, 2));

        viewModel.loadTree();

        assertEquals("—", singleItem(ORDER_1).materialRequestsLabel());
    }

    @Test
    void partialReleaseKeepsHistoricalSubmittedCoverageNotRequestable() {
        seedOrderWithItems(ORDER_1, "26106082", 5, ITEM_1A);
        queryApi.putItemState(
                ProductionWorkbenchUiTestSupport.itemProductionState(
                        ORDER_1,
                        ITEM_1A,
                        ItemProductionStateStatus.PARTIALLY_RELEASED,
                        5,
                        2,
                        3));
        // Pilot-like: submitted 3, requestable 2 — column must show 3/5 not 2/5.
        applicationApi.putCoverage(coverage(ORDER_1, ITEM_1A, 5, 3, 2, 3, 1, 2));

        viewModel.loadTree();

        assertEquals("Учтено: 3/5", singleItem(ORDER_1).materialRequestsLabel());
    }

    @Test
    void fullyRejectedWarehouseTransferDoesNotEraseCoverage() {
        seedOrderWithItems(ORDER_1, "26106075", 1, ITEM_1A);
        queryApi.putItemState(
                ProductionWorkbenchUiTestSupport.activeItemState(ORDER_1, ITEM_1A, 1));
        // submitted 1, outstanding 1, requestable 0 (transfer rejected / waiting supply)
        applicationApi.putCoverage(coverage(ORDER_1, ITEM_1A, 1, 1, 0, 1, 1, 0));

        viewModel.loadTree();

        assertEquals("Учтено: 1/1", singleItem(ORDER_1).materialRequestsLabel());
    }

    @Test
    void materialRequestEligibilityAllowsRepeatedWhenRequestableZero() {
        seedOrderWithItems(ORDER_1, "ORD-1", 5, ITEM_1A);
        queryApi.putItemState(
                ProductionWorkbenchUiTestSupport.activeItemState(ORDER_1, ITEM_1A, 5));
        applicationApi.putCoverage(coverage(ORDER_1, ITEM_1A, 5, 5, 0, 5, 5, 0));
        applicationApi.putOrderQuantityMode(ORDER_1, QuantityModeView.STANDARD, 1L);

        viewModel.loadTree();
        viewModel.selectOrder(ORDER_1);

        var step1 = viewModel.loadMaterialRequestStep1();
        assertTrue(step1.ok());
        assertEquals(1, step1.rows().size());
        assertEquals(0L, step1.rows().getFirst().requestableProductQuantity());
        // Tree still shows historical counted coverage.
        assertEquals("Учтено: 5/5", singleItem(ORDER_1).materialRequestsLabel());
    }

    private void seedOrderWithItems(
            UUID orderId, String orderNumber, long orderedTotal, UUID... itemIds) {
        worklistQuery.rows.add(
                ProductionWorkbenchUiTestSupport.worklistRow(orderId, orderNumber, "Клиент"));
        orderQuery.orders.put(
                OrderId.of(orderId), ProductionWorkbenchUiTestSupport.order(orderId, orderNumber));
        int i = 1;
        for (UUID itemId : itemIds) {
            orderQuery.items.add(
                    ProductionWorkbenchUiTestSupport.item(orderId, itemId, String.valueOf(i++)));
        }
        queryApi.listFacts.put(
                orderId,
                ProductionWorkbenchUiTestSupport.productionListFacts(
                        orderId,
                        OrderProductionViewStatus.IN_PRODUCTION,
                        orderedTotal,
                        0,
                        orderedTotal));
    }

    private static MaterialRequirementProductCoverageView coverage(
            UUID orderId,
            UUID itemId,
            long ordered,
            long active,
            long released,
            long submitted,
            long outstanding,
            long requestable) {
        return new MaterialRequirementProductCoverageView(
                orderId, itemId, ordered, active, released, submitted, outstanding, requestable);
    }

    private ProductionTreeNode singleOrder(UUID orderId) {
        return viewModel.visibleTreeProperty().get().stream()
                .filter(m -> m.orderNode().sourceOrderId().equals(orderId))
                .findFirst()
                .orElseThrow()
                .orderNode();
    }

    private ProductionTreeNode singleItem(UUID orderId) {
        List<ProductionTreeNode> items =
                viewModel.visibleTreeProperty().get().stream()
                        .filter(m -> m.orderNode().sourceOrderId().equals(orderId))
                        .findFirst()
                        .orElseThrow()
                        .items();
        assertEquals(1, items.size());
        return items.get(0);
    }
}
