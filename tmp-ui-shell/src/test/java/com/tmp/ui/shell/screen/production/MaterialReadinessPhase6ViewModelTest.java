package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.production.api.ProductionApplicationApi.MaterialReadinessLineView;
import com.tmp.production.api.ProductionApplicationApi.MaterialReadinessReasonView;
import com.tmp.production.api.ProductionApplicationApi.MaterialReadinessStatusView;
import com.tmp.production.api.ProductionApplicationApi.MaterialReadinessView;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.AllowAllAuthorization;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubApplicationApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubAuthentication;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubOrderQuery;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubQueryApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubWorklistQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaterialReadinessPhase6ViewModelTest {

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
    void orderCardShowsReadySummaryAndDetailsButton() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        applicationApi.remainingReadiness =
                new MaterialReadinessView(
                        MaterialReadinessStatusView.READY,
                        MaterialReadinessReasonView.NONE,
                        0,
                        List.of(line("101.208", "40", "50", "0")));

        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertEquals(
                "✓ Материалов достаточно для выпуска оставшихся изделий.",
                viewModel.materialsSummaryProperty().get());
        assertEquals("", viewModel.materialsDetailProperty().get());
        assertTrue(viewModel.materialsDetailsVisibleProperty().get());
        assertEquals(1, applicationApi.remainingReadinessCalls.size());
        assertEquals(0, applicationApi.checkCalls.size());
    }

    @Test
    void orderCardShowsNotReadyDeficientCountWithoutReceivedTerminology() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        applicationApi.remainingReadiness =
                new MaterialReadinessView(
                        MaterialReadinessStatusView.NOT_READY,
                        MaterialReadinessReasonView.INSUFFICIENT_STOCK,
                        3,
                        List.of(
                                line("A", "10", "5", "5"),
                                line("B", "8", "0", "8"),
                                line("C", "2", "1", "1")));

        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertEquals(
                "Недостаточно материалов для выпуска оставшихся изделий.",
                viewModel.materialsSummaryProperty().get());
        assertEquals("Не хватает: 3 позиции материалов", viewModel.materialsDetailProperty().get());
        assertTrue(viewModel.materialsDetailsVisibleProperty().get());
        String summary = viewModel.materialsSummaryProperty().get();
        assertFalse(summary.toLowerCase().contains("получено"));
        MaterialReadinessLineRow row =
                MaterialReadinessLineRow.from(applicationApi.remainingReadiness.lines().getFirst());
        assertEquals("10", row.requiredQuantity());
        assertEquals("5", row.availableQuantity());
        assertEquals("5", row.shortageQuantity());
        assertEquals("м", row.unitOfMeasure());
        assertFalse(row.materialCode().contains(ORDER_ID.toString()));
    }

    @Test
    void manufacturedShowsNoMisleadingReady() {
        seedDetailOrder(OrderProductionViewStatus.MANUFACTURED);
        applicationApi.remainingReadiness =
                new MaterialReadinessView(
                        MaterialReadinessStatusView.NOT_APPLICABLE,
                        MaterialReadinessReasonView.MANUFACTURED,
                        0,
                        List.of());

        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertEquals("Все изделия выпущены.", viewModel.materialsSummaryProperty().get());
        assertFalse(viewModel.materialsDetailsVisibleProperty().get());
    }

    @Test
    void notAcceptedShowsExplanatoryState() {
        seedDetailOrder(OrderProductionViewStatus.NOT_ACCEPTED);
        applicationApi.remainingReadiness =
                new MaterialReadinessView(
                        MaterialReadinessStatusView.NOT_APPLICABLE,
                        MaterialReadinessReasonView.NOT_ACCEPTED,
                        0,
                        List.of());

        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertEquals(
                "Материалы будут доступны после принятия заказа в производство.",
                viewModel.materialsSummaryProperty().get());
        assertFalse(viewModel.materialsDetailsVisibleProperty().get());
    }

    @Test
    void noProductionWarehouseMessage() {
        seedDetailOrder(OrderProductionViewStatus.IN_PRODUCTION);
        applicationApi.remainingReadiness =
                new MaterialReadinessView(
                        MaterialReadinessStatusView.NO_PRODUCTION_WAREHOUSE,
                        MaterialReadinessReasonView.NO_PRODUCTION_WAREHOUSE,
                        0,
                        List.of());

        viewModel.openForOrder(OrderId.of(ORDER_ID));

        assertEquals(
                "Не назначен производственный склад.",
                viewModel.materialsSummaryProperty().get());
        assertFalse(viewModel.materialsDetailsVisibleProperty().get());
    }

    private void seedDetailOrder(OrderProductionViewStatus status) {
        worklistQuery.rows.add(
                ProductionWorkbenchUiTestSupport.worklistRow(ORDER_ID, "ORD-1", "Клиент"));
        orderQuery.orders.put(
                OrderId.of(ORDER_ID), ProductionWorkbenchUiTestSupport.order(ORDER_ID, "ORD-1"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_ID, ITEM_A, "1"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_ID, ITEM_B, "2"));
        queryApi.listFacts.put(
                ORDER_ID,
                ProductionWorkbenchUiTestSupport.productionListFacts(
                        ORDER_ID, status, 20, 0, 20));
        queryApi.view = new OrderProductionView(ORDER_ID, status, 2, 20, 0, 0, 0);
        queryApi.statesByOrder.put(
                ORDER_ID,
                java.util.Map.of(
                        ITEM_A,
                        state(ITEM_A),
                        ITEM_B,
                        state(ITEM_B)));
    }

    private static ItemProductionStateView state(UUID itemId) {
        return new ItemProductionStateView(
                ORDER_ID,
                itemId,
                SPEC_ID,
                ItemProductionStateStatus.IN_PRODUCTION,
                10,
                10,
                10,
                0,
                Optional.empty(),
                Instant.parse("2026-01-01T10:00:00Z"),
                List.of());
    }

    private static MaterialReadinessLineView line(
            String code, String required, String available, String shortage) {
        return new MaterialReadinessLineView(
                UUID.randomUUID(),
                code,
                "Профиль",
                "Белый",
                "м",
                new BigDecimal(required),
                new BigDecimal(available),
                new BigDecimal(shortage));
    }
}
