package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementProductCoverageView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementProductSelectionView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementSourceItemView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementStatusView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementView;
import com.tmp.production.api.ProductionApplicationApi.ProductRequestKindView;
import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.AllowAllAuthorization;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubApplicationApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubAuthentication;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubOrderQuery;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubQueryApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubWorklistQuery;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchViewModel.TreeOrderModel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Focused Phase 2 UI regression for repeated Material Requests (checkbox, limits, prepare kinds,
 * draft kind display helpers, workbench repeated statistics).
 */
class MaterialRequestRepeatedPhase2Test {

    private static final UUID ORDER_1 = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID ORDER_2 = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2");
    private static final UUID ITEM_1A = UUID.fromString("cccccccc-cccc-4ccc-8ccc-ccccccccccc1");
    private static final UUID ITEM_1B = UUID.fromString("cccccccc-cccc-4ccc-8ccc-ccccccccccc2");
    private static final UUID ITEM_2A = UUID.fromString("dddddddd-dddd-4ddd-8ddd-ddddddddddd1");
    private static final UUID REQ_ID = UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1");
    private static final UUID LINE_ID = UUID.fromString("ffffffff-ffff-4fff-8fff-fffffffffff1");
    private static final UUID MAT_REF = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID WH = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID SPEC = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private StubQueryApi queryApi;
    private StubApplicationApi applicationApi;
    private StubOrderQuery orderQuery;
    private StubWorklistQuery worklistQuery;
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
        viewModel =
                new ProductionWorkbenchViewModel(
                        queryApi,
                        applicationApi,
                        orderQuery,
                        worklistQuery,
                        new ProductionWorkbenchUiTestSupport.StubWarehouseApi(),
                        new AllowAllAuthorization(),
                        new StubAuthentication());
    }

    @Test
    void a_normalRequestKeepsRequestableMaximumWhenCheckboxOff() {
        MaterialRequestQuantityRow row =
                row(ORDER_1, ITEM_1A, QuantityModeView.FLEXIBLE, 3, 5, 3);
        assertFalse(row.repeatedRequest());
        assertEquals(3L, row.maximumProductQuantity());
        row.setRequestedProductQuantity(3L);
        assertTrue(MaterialRequestDialogSupport.validateStep1Quantities(List.of(row)).isEmpty());
        row.setRequestedProductQuantity(4L);
        assertTrue(MaterialRequestDialogSupport.validateStep1Quantities(List.of(row)).isPresent());
    }

    @Test
    void b_repeatedAllowsOrderedQuantityAndRejectsAboveOrdered() {
        MaterialRequestQuantityRow row =
                row(ORDER_1, ITEM_1A, QuantityModeView.FLEXIBLE, 2, 5, 2);
        row.setRepeatedRequest(true);
        assertEquals(5L, row.maximumProductQuantity());
        row.setRequestedProductQuantity(5L);
        assertTrue(MaterialRequestDialogSupport.validateStep1Quantities(List.of(row)).isEmpty());
        row.setRequestedProductQuantity(6L);
        assertTrue(MaterialRequestDialogSupport.validateStep1Quantities(List.of(row)).isPresent());
    }

    @Test
    void c_zeroNormalAvailabilityStillAccessibleForRepeated() {
        seedOrder(ORDER_1, "26106075", 5, ITEM_1A, 5, 0);
        applicationApi.putCoverage(coverage(ORDER_1, ITEM_1A, 5, 5, 0, 5, 0, 0, 0));
        applicationApi.putOrderQuantityMode(ORDER_1, QuantityModeView.STANDARD, 1L);
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_1, ITEM_1A), true);

        var loaded = viewModel.loadMaterialRequestStep1();
        assertTrue(loaded.ok());
        MaterialRequestQuantityRow row = loaded.rows().getFirst();
        assertEquals(0L, row.requestableProductQuantity());
        assertEquals(5L, row.orderedQuantity());
        row.setRepeatedRequest(true);
        assertEquals(1L, row.requestedProductQuantity());
        row.setRequestedProductQuantity(5L);
        assertTrue(MaterialRequestDialogSupport.validateStep1Quantities(List.of(row)).isEmpty());
    }

    @Test
    void d_partialReleaseRepeatedMaxRemainsOrdered() {
        MaterialRequestQuantityRow row =
                row(ORDER_1, ITEM_1A, QuantityModeView.STANDARD, 2, 5, 2);
        row.setRepeatedRequest(true);
        assertEquals(5L, row.maximumProductQuantity());
        // Valid previous quantity 2 is preserved within 1..ordered.
        assertEquals(2L, row.requestedProductQuantity());
        MaterialRequestQuantityRow zeroed =
                row(ORDER_1, ITEM_1A, QuantityModeView.STANDARD, 2, 5, 0);
        zeroed.setRepeatedRequest(true);
        assertEquals(1L, zeroed.requestedProductQuantity());
    }

    @Test
    void e_standardNormalUnchangedRepeatedExplicitlyEditable() {
        MaterialRequestQuantityRow row =
                row(ORDER_1, ITEM_1A, QuantityModeView.STANDARD, 3, 5, 3);
        assertFalse(row.quantityEditable());
        assertEquals(3L, MaterialRequestDialogSupport.resolvedProductQuantityForPrepare(row));
        row.setRepeatedRequest(true);
        assertTrue(row.quantityEditable());
        row.setRequestedProductQuantity(4L);
        assertEquals(4L, MaterialRequestDialogSupport.resolvedProductQuantityForPrepare(row));
        row.setRepeatedRequest(false);
        assertFalse(row.quantityEditable());
        assertEquals(3L, row.requestedProductQuantity());
        assertEquals(3L, MaterialRequestDialogSupport.resolvedProductQuantityForPrepare(row));
    }

    @Test
    void f_flexibleNormalAndRepeatedHaveSeparateLimits() {
        MaterialRequestQuantityRow row =
                row(ORDER_1, ITEM_1A, QuantityModeView.FLEXIBLE, 3, 5, 3);
        assertEquals(3L, row.maximumProductQuantity());
        row.setRequestedProductQuantity(2L);
        row.setRepeatedRequest(true);
        assertEquals(5L, row.maximumProductQuantity());
        assertEquals(2L, row.requestedProductQuantity());
        row.setRepeatedRequest(false);
        assertEquals(3L, row.maximumProductQuantity());
        assertEquals(2L, row.requestedProductQuantity());
    }

    @Test
    void g_mixedCountedAndRepeatedReachPrepareApi() {
        seedOrder(ORDER_1, "MIX-1", 5, ITEM_1A, 5, 0);
        seedOrder(ORDER_2, "MIX-2", 4, ITEM_2A, 4, 0);
        applicationApi.putCoverage(coverage(ORDER_1, ITEM_1A, 5, 5, 0, 2, 3, 0, 0));
        applicationApi.putCoverage(coverage(ORDER_2, ITEM_2A, 4, 4, 0, 0, 4, 0, 0));
        applicationApi.putOrderQuantityMode(ORDER_1, QuantityModeView.STANDARD, 1L);
        applicationApi.putOrderQuantityMode(ORDER_2, QuantityModeView.FLEXIBLE, 1L);
        applicationApi.requirement =
                draft(
                        List.of(
                                source(ORDER_1, ITEM_1A, 3, ProductRequestKindView.COUNTED),
                                source(ORDER_2, ITEM_2A, 4, ProductRequestKindView.REPEATED)));
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_1, ITEM_1A), true);
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_2, ITEM_2A), true);

        var loaded = viewModel.loadMaterialRequestStep1();
        assertTrue(loaded.ok());
        assertEquals(2, loaded.rows().size());
        MaterialRequestQuantityRow counted = loaded.rows().get(0);
        MaterialRequestQuantityRow repeated = loaded.rows().get(1);
        if (counted.sourceOrderItemId().equals(ITEM_2A)) {
            MaterialRequestQuantityRow tmp = counted;
            counted = repeated;
            repeated = tmp;
        }
        repeated.setRepeatedRequest(true);
        repeated.setRequestedProductQuantity(4L);

        viewModel.prepareMaterialRequirement(List.of(counted, repeated));

        List<MaterialRequirementProductSelectionView> selections =
                applicationApi.lastPrepareSelections;
        assertEquals(2, selections.size());
        MaterialRequirementProductSelectionView countedSel =
                selections.stream()
                        .filter(s -> s.sourceOrderItemId().equals(ITEM_1A))
                        .findFirst()
                        .orElseThrow();
        MaterialRequirementProductSelectionView repeatedSel =
                selections.stream()
                        .filter(s -> s.sourceOrderItemId().equals(ITEM_2A))
                        .findFirst()
                        .orElseThrow();
        assertFalse(countedSel.repeatedRequest());
        assertEquals(Optional.empty(), countedSel.requestedProductQuantity());
        assertTrue(repeatedSel.repeatedRequest());
        assertEquals(Optional.of(4L), repeatedSel.requestedProductQuantity());

        List<String> summary = viewModel.materialRequirementSourceSummary(applicationApi.requirement);
        assertTrue(summary.stream().anyMatch(s -> s.contains("Обычный запрос")));
        assertTrue(summary.stream().anyMatch(s -> s.contains("Повторный запрос")));

        List<MaterialRequestDialogSupport.RepeatedSubmitConfirmRow> confirm =
                viewModel.materialRequirementRepeatedSubmitConfirmRows(applicationApi.requirement);
        assertEquals(1, confirm.size());
        assertEquals(4L, confirm.getFirst().requestedProductQuantity());
        assertTrue(
                MaterialRequestDialogSupport.requiresRepeatedSubmitConfirmation(
                        applicationApi.requirement));
    }

    @Test
    void h_draftReopenUsesPersistedRequestKindForConfirmation() {
        MaterialRequirementView draft =
                draft(List.of(source(ORDER_1, ITEM_1A, 5, ProductRequestKindView.REPEATED)));
        applicationApi.requirement = draft;
        seedOrder(ORDER_1, "DRAFT-1", 5, ITEM_1A, 5, 0);
        viewModel.loadTree();

        Optional<MaterialRequirementView> reopened = viewModel.getMaterialRequirement(REQ_ID);
        assertTrue(reopened.isPresent());
        assertEquals(
                ProductRequestKindView.REPEATED,
                reopened.get().sourceItems().getFirst().productRequestKind());
        List<String> summary = viewModel.materialRequirementSourceSummary(reopened.get());
        assertTrue(summary.getFirst().contains("Повторный запрос"));
        assertEquals(
                1, viewModel.materialRequirementRepeatedSubmitConfirmRows(reopened.get()).size());
    }

    @Test
    void i_workbenchRepeatedStatisticsIgnoreDraftAndShowSubmittedOnly() {
        seedOrder(ORDER_1, "26106082", 5, ITEM_1A, 3, 2);
        applicationApi.putCoverage(coverage(ORDER_1, ITEM_1A, 5, 3, 2, 3, 2, 5, 2));
        viewModel.loadTree();

        String label = singleItem(ORDER_1).materialRequestsLabel();
        assertTrue(label.startsWith("Учтено: 3/5"));
        assertTrue(label.contains("Повторно: 5 изд. (2 запроса)"));
        assertTrue(MaterialRequestCoverageLabels.hasRepeated(label));
    }

    @Test
    void j_parentAggregatesRepeatedSourceItemCounts() {
        seedOrder(ORDER_1, "PARENT", 8, ITEM_1A, ITEM_1B);
        queryApi.putItemState(
                ProductionWorkbenchUiTestSupport.activeItemState(ORDER_1, ITEM_1A, 5));
        queryApi.putItemState(
                ProductionWorkbenchUiTestSupport.activeItemState(ORDER_1, ITEM_1B, 3));
        applicationApi.putCoverage(coverage(ORDER_1, ITEM_1A, 5, 5, 0, 2, 3, 4, 1));
        applicationApi.putCoverage(coverage(ORDER_1, ITEM_1B, 3, 3, 0, 1, 2, 3, 2));
        viewModel.loadTree();

        TreeOrderModel model = viewModel.visibleTreeProperty().get().get(0);
        assertEquals(
                "Учтено: 2/5\nПовторно: 4 изд. (1 запрос)",
                model.items().get(0).materialRequestsLabel());
        assertEquals(
                "Учтено: 1/3\nПовторно: 3 изд. (2 запроса)",
                model.items().get(1).materialRequestsLabel());
        // M is sum of Phase 1 per-source-item counts (1+2), not unique MR documents.
        assertEquals(
                "Учтено: 3/8\nПовторно: 7 изд. (3 запроса)",
                model.orderNode().materialRequestsLabel());
    }

    @Test
    void j_noRequestPresentationRemainsDash() {
        seedOrder(ORDER_1, "EMPTY", 2, ITEM_1A, 2, 0);
        applicationApi.putCoverage(coverage(ORDER_1, ITEM_1A, 2, 2, 0, 0, 2, 0, 0));
        viewModel.loadTree();
        assertEquals("—", singleItem(ORDER_1).materialRequestsLabel());
        assertEquals("—", singleOrder(ORDER_1).materialRequestsLabel());
    }

    @Test
    void k_releasedAndCancelledRemainBlocked() {
        seedOrder(ORDER_1, "REL", 5, ITEM_1A, 0, 5);
        applicationApi.putCoverage(coverage(ORDER_1, ITEM_1A, 5, 0, 5, 5, 0, 0, 0));
        applicationApi.putOrderQuantityMode(ORDER_1, QuantityModeView.STANDARD, 1L);
        viewModel.loadTree();
        assertFalse(singleItem(ORDER_1).selectable());

        seedOrder(ORDER_2, "CAN", 3, ITEM_2A, 0, 0);
        queryApi.putItemState(
                ProductionWorkbenchUiTestSupport.itemProductionState(
                        ORDER_2,
                        ITEM_2A,
                        ItemProductionStateStatus.CANCELLED,
                        3,
                        0,
                        0));
        applicationApi.putCoverage(coverage(ORDER_2, ITEM_2A, 3, 0, 0, 0, 0, 0, 0));
        viewModel.loadTree();
        assertFalse(singleItem(ORDER_2).selectable());
    }

    @Test
    void coverageLabelsFormatRepeatedCompactAndTwoLine() {
        assertEquals("Учтено: 3/5", MaterialRequestCoverageLabels.format(3, 5, 0, 0));
        assertEquals(
                "Учтено: 3/5\nПовторно: 2 изд. (1 запрос)",
                MaterialRequestCoverageLabels.format(3, 5, 2, 1));
        assertEquals(
                "—\nПовторно: 5 изд. (2 запроса)",
                MaterialRequestCoverageLabels.format(0, 5, 5, 2));
        assertFalse(MaterialRequestCoverageLabels.hasRepeated("Учтено: 1/1"));
    }

    @Test
    void confirmationListsOnlyRepeatedFromPersistedKinds() {
        MaterialRequirementView mixed =
                draft(
                        List.of(
                                source(ORDER_1, ITEM_1A, 3, ProductRequestKindView.COUNTED),
                                source(ORDER_1, ITEM_1B, 2, ProductRequestKindView.REPEATED),
                                source(ORDER_2, ITEM_2A, 1, ProductRequestKindView.LEGACY_EXCLUDED)));
        seedOrder(ORDER_1, "C1", 8, ITEM_1A, ITEM_1B);
        seedOrder(ORDER_2, "C2", 1, ITEM_2A, 1, 0);
        viewModel.loadTree();
        List<MaterialRequestDialogSupport.RepeatedSubmitConfirmRow> rows =
                viewModel.materialRequirementRepeatedSubmitConfirmRows(mixed);
        assertEquals(1, rows.size());
        assertEquals(2L, rows.getFirst().requestedProductQuantity());
        List<String> display =
                MaterialRequestDialogSupport.repeatedConfirmDisplayLines(
                        rows, viewModel.materialRequirementSourceSummary(mixed));
        assertEquals(1, display.size());
        assertTrue(display.getFirst().contains("2 изд."));
    }

    private void seedOrder(
            UUID orderId, String number, long orderedTotal, UUID itemId, long active, long released) {
        seedOrder(orderId, number, orderedTotal, itemId);
        ItemProductionStateStatus status;
        if (active <= 0L && released > 0L) {
            status = ItemProductionStateStatus.RELEASED;
        } else if (released > 0L) {
            status = ItemProductionStateStatus.PARTIALLY_RELEASED;
        } else {
            status = ItemProductionStateStatus.IN_PRODUCTION;
        }
        queryApi.putItemState(
                ProductionWorkbenchUiTestSupport.itemProductionState(
                        orderId, itemId, status, orderedTotal, released, active));
    }

    private void seedOrder(UUID orderId, String number, long orderedTotal, UUID... itemIds) {
        worklistQuery.rows.add(
                ProductionWorkbenchUiTestSupport.worklistRow(orderId, number, "Cust"));
        orderQuery.orders.put(
                OrderId.of(orderId), ProductionWorkbenchUiTestSupport.order(orderId, number));
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
        return items.getFirst();
    }

    private static MaterialRequestQuantityRow row(
            UUID orderId,
            UUID itemId,
            QuantityModeView mode,
            long requestable,
            long ordered,
            long defaultQty) {
        return new MaterialRequestQuantityRow(
                orderId, itemId, "ORD", "Поз. 1", "Изделие", mode, requestable, ordered, defaultQty);
    }

    private static MaterialRequirementProductCoverageView coverage(
            UUID orderId,
            UUID itemId,
            long ordered,
            long active,
            long released,
            long submitted,
            long requestable,
            long repeatedQty,
            long repeatedCount) {
        return new MaterialRequirementProductCoverageView(
                orderId,
                itemId,
                ordered,
                active,
                released,
                submitted,
                Math.max(0L, submitted - released),
                requestable,
                repeatedQty,
                repeatedCount);
    }

    private static MaterialRequirementSourceItemView source(
            UUID orderId, UUID itemId, long qty, ProductRequestKindView kind) {
        return new MaterialRequirementSourceItemView(orderId, itemId, qty, kind);
    }

    private static MaterialRequirementView draft(List<MaterialRequirementSourceItemView> sources) {
        return new MaterialRequirementView(
                REQ_ID,
                sources,
                WH,
                Instant.parse("2026-11-02T06:00:00Z"),
                Instant.parse("2026-11-02T06:00:00Z"),
                0L,
                MaterialRequirementStatusView.DRAFT,
                Optional.empty(),
                Optional.empty(),
                List.of(
                        new com.tmp.production.api.ProductionApplicationApi.MaterialRequirementLineView(
                                LINE_ID,
                                MAT_REF,
                                "101",
                                "Материал",
                                "Белый",
                                "м",
                                null,
                                new BigDecimal("10.000"),
                                new BigDecimal("10.000"),
                                sources.stream()
                                        .map(MaterialRequirementSourceItemView::sourceOrderItemId)
                                        .toList(),
                                null)));
    }
}
