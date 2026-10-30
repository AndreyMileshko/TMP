package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.production.api.ProductionApplicationApi.MaterialReadinessLineView;
import com.tmp.production.api.ProductionApplicationApi.MaterialReadinessReasonView;
import com.tmp.production.api.ProductionApplicationApi.MaterialReadinessStatusView;
import com.tmp.production.api.ProductionApplicationApi.MaterialReadinessView;
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
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubWarehouseApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubWorklistQuery;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReleasePhase7ViewModelTest {

    private static final UUID ORDER_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORDER_B = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ORDER_C = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID ITEM_A1 = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID ITEM_B2 = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2");
    private static final UUID ITEM_C3 = UUID.fromString("cccccccc-cccc-4ccc-8ccc-ccccccccccc3");
    private static final UUID SPEC = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID CELL = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final UUID MAT = UUID.fromString("77777777-7777-4777-8777-777777777777");

    private StubQueryApi queryApi;
    private StubApplicationApi applicationApi;
    private StubOrderQuery orderQuery;
    private StubWorklistQuery worklistQuery;
    private StubWarehouseApi warehouseApi;
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
        warehouseApi = new StubWarehouseApi();
        auth = new AllowAllAuthorization();
        authentication = new StubAuthentication();
        viewModel =
                new ProductionWorkbenchViewModel(
                        queryApi,
                        applicationApi,
                        orderQuery,
                        worklistQuery,
                        warehouseApi,
                        auth,
                        authentication);
    }

    @Test
    void releaseDisabledWithoutSelection() {
        seedOrders();
        viewModel.loadTree();
        assertTrue(viewModel.canReleaseProperty().get());
        assertFalse(viewModel.releaseEnabledProperty().get());
    }

    @Test
    void releaseHiddenWithoutPermission() {
        auth.allowed =
                Set.of(
                        UiShellScreens.PRODUCTION_VIEW_PERMISSION,
                        UiShellScreens.PRODUCTION_ACCEPT_PERMISSION);
        viewModel =
                new ProductionWorkbenchViewModel(
                        queryApi,
                        applicationApi,
                        orderQuery,
                        worklistQuery,
                        warehouseApi,
                        auth,
                        authentication);
        seedOrders();
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
        assertFalse(viewModel.canReleaseProperty().get());
        assertFalse(viewModel.releaseEnabledProperty().get());
    }

    @Test
    void standardQuantityIsReadOnlyFullActiveRemainder() {
        seedOrders();
        applicationApi.putOrderQuantityMode(ORDER_A, QuantityModeView.STANDARD, 1L);
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);

        var loaded = viewModel.loadReleaseStep1();
        assertTrue(loaded.ok());
        ReleaseQuantityRow row = loaded.rows().getFirst();
        assertTrue(row.standardMode());
        assertEquals(4L, row.activeProductionQuantity());
        assertEquals(4L, ReleaseDialogSupport.resolvedReleaseQuantity(row));
        assertTrue(ReleaseDialogSupport.validateStep1Quantities(List.of(row)).isEmpty());
    }

    @Test
    void flexibleQuantityDefaultAndValidationBounds() {
        seedOrders();
        applicationApi.putOrderQuantityMode(ORDER_A, QuantityModeView.FLEXIBLE, 1L);
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);

        var loaded = viewModel.loadReleaseStep1();
        ReleaseQuantityRow row = loaded.rows().getFirst();
        assertFalse(row.standardMode());
        assertEquals(4L, row.releaseQuantity());

        row.setReleaseQuantity(2L);
        assertTrue(ReleaseDialogSupport.validateStep1Quantities(List.of(row)).isEmpty());
        assertEquals(2L, ReleaseDialogSupport.resolvedReleaseQuantity(row));

        row.setReleaseQuantity(0L);
        assertTrue(ReleaseDialogSupport.validateStep1Quantities(List.of(row)).isPresent());
        row.setReleaseQuantity(5L);
        assertTrue(ReleaseDialogSupport.validateStep1Quantities(List.of(row)).isPresent());
    }

    @Test
    void mixedModesAcrossOrdersHaveCorrectQuantities() {
        seedOrders();
        applicationApi.putOrderQuantityMode(ORDER_A, QuantityModeView.STANDARD, 1L);
        applicationApi.putOrderQuantityMode(ORDER_B, QuantityModeView.FLEXIBLE, 1L);
        applicationApi.putOrderQuantityMode(ORDER_C, QuantityModeView.STANDARD, 1L);
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_B, ITEM_B2), true);
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_C, ITEM_C3), true);

        var loaded = viewModel.loadReleaseStep1();
        assertTrue(loaded.ok());
        assertEquals(3, loaded.rows().size());

        ReleaseQuantityRow flexible =
                loaded.rows().stream()
                        .filter(r -> r.sourceOrderId().equals(ORDER_B))
                        .findFirst()
                        .orElseThrow();
        flexible.setReleaseQuantity(3L);

        assertEquals(
                4L,
                ReleaseDialogSupport.resolvedReleaseQuantity(
                        loaded.rows().stream()
                                .filter(r -> r.sourceOrderId().equals(ORDER_A))
                                .findFirst()
                                .orElseThrow()));
        assertEquals(3L, ReleaseDialogSupport.resolvedReleaseQuantity(flexible));
        assertEquals(
                2L,
                ReleaseDialogSupport.resolvedReleaseQuantity(
                        loaded.rows().stream()
                                .filter(r -> r.sourceOrderId().equals(ORDER_C))
                                .findFirst()
                                .orElseThrow()));
    }

    @Test
    void invalidSelectedItemNotSilentlySkipped() {
        seedOrders();
        queryApi.putItemState(
                state(
                        ORDER_A,
                        ITEM_A1,
                        ItemProductionStateStatus.RELEASED,
                        10L,
                        10L,
                        0L));
        viewModel.loadTree();
        // Bypass UI selectability guard: mutation path must not silently drop invalid refs.
        viewModel.selectionModel().selectItem(new ProductionOrderItemRef(ORDER_A, ITEM_A1));
        var loaded = viewModel.loadReleaseStep1();
        assertFalse(loaded.ok());
        assertTrue(loaded.validationMessage().contains("активное количество"));
    }

    @Test
    void readinessReadyContinuesPreliminaryShortageAdvisoryFatalBlocks() {
        seedOrders();
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
        var rows = viewModel.loadReleaseStep1().rows();

        applicationApi.remainingReadiness =
                new MaterialReadinessView(
                        MaterialReadinessStatusView.READY,
                        MaterialReadinessReasonView.NONE,
                        0,
                        List.of());
        var ready = viewModel.checkReleaseReadiness(rows);
        assertTrue(ready.ready());
        assertFalse(ready.preliminaryShortage());
        assertFalse(ready.fatalBlocked());

        applicationApi.remainingReadiness =
                new MaterialReadinessView(
                        MaterialReadinessStatusView.NOT_READY,
                        MaterialReadinessReasonView.INSUFFICIENT_STOCK,
                        1,
                        List.of());
        var shortage = viewModel.checkReleaseReadiness(rows);
        assertFalse(shortage.ready());
        assertTrue(shortage.preliminaryShortage());
        assertFalse(shortage.fatalBlocked());

        applicationApi.remainingReadiness =
                new MaterialReadinessView(
                        MaterialReadinessStatusView.NO_PRODUCTION_WAREHOUSE,
                        MaterialReadinessReasonView.NO_PRODUCTION_WAREHOUSE,
                        0,
                        List.of());
        var noWarehouse = viewModel.checkReleaseReadiness(rows);
        assertFalse(noWarehouse.ready());
        assertFalse(noWarehouse.preliminaryShortage());
        assertTrue(noWarehouse.fatalBlocked());
    }

    @Test
    void unresolvedReadinessUsesIdentityMessageWithoutDetails() {
        MaterialReadinessView unresolved =
                new MaterialReadinessView(
                        MaterialReadinessStatusView.MATERIAL_REFERENCE_UNRESOLVED,
                        MaterialReadinessReasonView.MATERIAL_REFERENCE_UNRESOLVED,
                        0,
                        List.of());
        assertFalse(ReleaseDialogSupport.isPreliminaryMaterialShortage(unresolved));
        assertEquals(
                ProductionUiErrorMapper.MATERIALS_UNRESOLVED,
                ReleaseDialogSupport.readinessBlockedHeader(unresolved));
        assertFalse(ReleaseDialogSupport.readinessDetailsAvailable(unresolved));
        assertFalse(
                ReleaseDialogSupport.readinessBlockedHeader(unresolved)
                        .contains("Недостаточно материалов"));
    }

    @Test
    void preliminaryShortageIsAdvisoryWithDetailsAccess() {
        MaterialReadinessLineView shortageLine =
                new MaterialReadinessLineView(
                        MAT,
                        "MAT-1",
                        "Профиль",
                        "Белый",
                        "м",
                        new BigDecimal("14.712"),
                        new BigDecimal("13"),
                        new BigDecimal("1.712"),
                        null,
                        null);
        MaterialReadinessView shortage =
                new MaterialReadinessView(
                        MaterialReadinessStatusView.NOT_READY,
                        MaterialReadinessReasonView.INSUFFICIENT_STOCK,
                        1,
                        List.of(shortageLine));
        assertTrue(ReleaseDialogSupport.isPreliminaryMaterialShortage(shortage));
        assertTrue(ReleaseDialogSupport.readinessDetailsAvailable(shortage));
        assertTrue(
                ProductionUiErrorMapper.RELEASE_PRELIMINARY_SHORTAGE_ADVISORY.contains(
                        "Расчётная потребность превышает остаток"));
        assertTrue(
                ProductionUiErrorMapper.RELEASE_PRELIMINARY_SHORTAGE_ADVISORY.contains(
                        "указать фактический расход"));
        assertFalse(
                ProductionUiErrorMapper.RELEASE_PRELIMINARY_SHORTAGE_ADVISORY.contains(
                        "Материалы проверены"));
    }

    @Test
    void confirmSummaryDoesNotClaimMaterialsAlreadyVerified() {
        ReleaseQuantityRow row =
                new ReleaseQuantityRow(
                        ORDER_A,
                        ITEM_A1,
                        "Заказ №4183",
                        "1",
                        "Окно",
                        QuantityModeView.STANDARD,
                        10L,
                        6L,
                        4L,
                        4L);
        String content = ReleaseDialogSupport.confirmSummaryContent(List.of(row), List.of());
        assertFalse(content.contains("Материалы проверены"));
        assertTrue(content.contains(ProductionUiErrorMapper.RELEASE_CONFIRM_STOCK_NOTE));
        assertTrue(content.contains("Изделий к выпуску: 4"));
    }

    @Test
    void multiOrderPreliminaryShortageNotSilentlyIgnored() {
        seedOrders();
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_B, ITEM_B2), true);
        var rows = viewModel.loadReleaseStep1().rows();

        applicationApi.releaseReadinessByOrder.put(
                ORDER_A,
                new MaterialReadinessView(
                        MaterialReadinessStatusView.READY,
                        MaterialReadinessReasonView.NONE,
                        0,
                        List.of()));
        MaterialReadinessLineView shortageLine =
                new MaterialReadinessLineView(
                        MAT,
                        "MAT-1",
                        "Профиль",
                        "Белый",
                        "м",
                        new BigDecimal("14.712"),
                        BigDecimal.ZERO,
                        new BigDecimal("14.712"),
                        null,
                        null);
        applicationApi.releaseReadinessByOrder.put(
                ORDER_B,
                new MaterialReadinessView(
                        MaterialReadinessStatusView.NOT_READY,
                        MaterialReadinessReasonView.INSUFFICIENT_STOCK,
                        1,
                        List.of(shortageLine)));

        var result = viewModel.checkReleaseReadiness(rows);
        assertTrue(result.preliminaryShortage());
        assertFalse(result.fatalBlocked());
        assertFalse(result.ready());
        assertEquals(2, applicationApi.releaseReadinessCalls.size());
    }

    @Test
    void multiOrderFatalPreferredOverPreliminaryShortage() {
        seedOrders();
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_B, ITEM_B2), true);
        var rows = viewModel.loadReleaseStep1().rows();

        applicationApi.releaseReadinessByOrder.put(
                ORDER_A,
                new MaterialReadinessView(
                        MaterialReadinessStatusView.NOT_READY,
                        MaterialReadinessReasonView.INSUFFICIENT_STOCK,
                        1,
                        List.of()));
        applicationApi.releaseReadinessByOrder.put(
                ORDER_B,
                new MaterialReadinessView(
                        MaterialReadinessStatusView.MATERIAL_REFERENCE_UNRESOLVED,
                        MaterialReadinessReasonView.MATERIAL_REFERENCE_UNRESOLVED,
                        0,
                        List.of()));

        var result = viewModel.checkReleaseReadiness(rows);
        assertTrue(result.fatalBlocked());
        assertFalse(result.preliminaryShortage());
        assertEquals(
                MaterialReadinessStatusView.MATERIAL_REFERENCE_UNRESOLVED,
                result.blocking().status());
    }

    @Test
    void readinessUsesSelectedReleaseQuantities() {
        seedOrders();
        applicationApi.putOrderQuantityMode(ORDER_A, QuantityModeView.FLEXIBLE, 1L);
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
        var rows = viewModel.loadReleaseStep1().rows();
        rows.getFirst().setReleaseQuantity(2L);

        viewModel.checkReleaseReadiness(rows);
        assertEquals(1, applicationApi.releaseReadinessCalls.size());
        assertEquals(
                2L, applicationApi.releaseReadinessCalls.getFirst().getFirst().releaseQuantity());
    }

    @Test
    void planFactLoadsDefaultActualsWithoutManualCellAllocations() {
        seedOrders();
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
        var quantityRows = viewModel.loadReleaseStep1().rows();
        List<ReleaseMaterialRow> materials = viewModel.prepareReleaseMaterialRows(quantityRows);
        assertFalse(materials.isEmpty());
        ReleaseMaterialRow material = materials.getFirst();
        assertFalse(material.plannedQuantity().isBlank());
        assertEquals("MAT-001", material.materialCode());
        assertEquals(MAT, material.materialReferenceId());
        assertEquals(ORDER_A, material.sourceOrderId());
        String plannedBeforeEdit = material.plannedQuantity();
        material.setActualQuantity("9");
        assertEquals("9", material.actualQuantity());
        assertEquals(plannedBeforeEdit, material.plannedQuantity());
        assertEquals("MAT-001", material.materialCode());
        assertEquals(MAT, material.materialReferenceId());
        Optional<String> validation = ReleaseDialogSupport.validatePlanFact(materials);
        assertTrue(validation.isEmpty());
    }

    @Test
    void prepareReleaseMaterialRowsKeepsArticlePerOrderAndMaterial() {
        seedOrders();
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_B, ITEM_B2), true);
        var quantityRows = viewModel.loadReleaseStep1().rows();
        List<ReleaseMaterialRow> materials = viewModel.prepareReleaseMaterialRows(quantityRows);
        assertEquals(2, materials.size());
        assertEquals(ORDER_A, materials.get(0).sourceOrderId());
        assertEquals(ORDER_B, materials.get(1).sourceOrderId());
        assertEquals(ITEM_A1, materials.get(0).sourceOrderItemId());
        assertEquals(ITEM_B2, materials.get(1).sourceOrderItemId());
        assertEquals("MAT-001", materials.get(0).materialCode());
        assertEquals("MAT-001", materials.get(1).materialCode());
        assertEquals(MAT, materials.get(0).materialReferenceId());
        assertEquals(MAT, materials.get(1).materialReferenceId());
    }

    @Test
    void multiOrderConfirmStopsAfterFirstFailure() {
        seedOrders();
        applicationApi.putOrderQuantityMode(ORDER_A, QuantityModeView.STANDARD, 1L);
        applicationApi.putOrderQuantityMode(ORDER_B, QuantityModeView.STANDARD, 1L);
        applicationApi.putOrderQuantityMode(ORDER_C, QuantityModeView.STANDARD, 1L);
        applicationApi.releaseFailuresByOrder.put(
                ORDER_B,
                new RuntimeException(
                        "Insufficient production warehouse stock for material "
                                + MAT
                                + " in cell "
                                + CELL
                                + ": available=1, required=10"));
        queryApi.view =
                new OrderProductionView(
                        ORDER_A, OrderProductionViewStatus.IN_PRODUCTION, 1, 1, 0, 0, 0);
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_B, ITEM_B2), true);
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_C, ITEM_C3), true);

        var quantityRows = viewModel.loadReleaseStep1().rows();
        List<ReleaseMaterialRow> materials = allocateDefaults(quantityRows);

        var result = viewModel.confirmReleases(quantityRows, materials);
        assertEquals(3, result.outcomes().size());
        assertTrue(result.outcomes().get(0).success());
        assertFalse(result.outcomes().get(1).success());
        assertTrue(result.outcomes().get(2).skipped());
        assertTrue(result.summaryMessage().contains("частично"));
        assertEquals(List.of(ORDER_A, ORDER_B), applicationApi.releaseOrderCalls);
    }

    @Test
    void fullSuccessApplyReleaseResultShowsSuccessAndClearsError() {
        seedOrders();
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
        var quantityRows = viewModel.loadReleaseStep1().rows();
        List<ReleaseMaterialRow> materials = allocateDefaults(quantityRows);

        var result = viewModel.confirmReleases(quantityRows, materials);
        assertTrue(result.allSuccess());
        assertTrue(result.anySuccess());

        var kind = viewModel.applyReleaseResult(result, quantityRows);
        assertEquals(
                ProductionWorkbenchViewModel.ReleaseCompletionKind.FULL_SUCCESS, kind);
        assertTrue(viewModel.statusMessageProperty().get().contains("Выпущено"));
        assertEquals("", viewModel.errorMessageProperty().get());
        assertFalse(
                viewModel
                        .selectionModel()
                        .isItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1)));
    }

    @Test
    void totalFailureApplyReleaseResultShowsWarningPathWithoutSuccessCleanup() {
        seedOrders();
        applicationApi.releaseFailuresByOrder.put(
                ORDER_A,
                new RuntimeException(
                        "Insufficient production warehouse stock for material "
                                + MAT
                                + " in cell "
                                + CELL
                                + ": available=0, required=12.5"));
        viewModel.loadTree();
        ProductionOrderItemRef ref = new ProductionOrderItemRef(ORDER_A, ITEM_A1);
        viewModel.setItemSelected(ref, true);
        var quantityRows = viewModel.loadReleaseStep1().rows();
        List<ReleaseMaterialRow> materials = allocateDefaults(quantityRows);

        var result = viewModel.confirmReleases(quantityRows, materials);
        assertFalse(result.allSuccess());
        assertFalse(result.anySuccess());
        assertTrue(result.summaryMessage().contains("Недостаточно материала"));
        assertTrue(result.summaryMessage().contains("Доступно: 0"));
        assertTrue(result.summaryMessage().contains("Требуется: 12.5"));
        assertTrue(result.summaryMessage().contains("Выпуск не выполнен"));

        var kind = viewModel.applyReleaseResult(result, quantityRows);
        assertEquals(
                ProductionWorkbenchViewModel.ReleaseCompletionKind.TOTAL_FAILURE, kind);
        assertEquals("", viewModel.statusMessageProperty().get());
        assertEquals(result.summaryMessage(), viewModel.errorMessageProperty().get());
        assertTrue(viewModel.selectionModel().isItemSelected(ref));
        assertFalse(viewModel.statusMessageProperty().get().contains("Выпущено"));
    }

    @Test
    void partialSuccessApplyReleaseResultKeepsFailedSelectionAndWarns() {
        seedOrders();
        applicationApi.putOrderQuantityMode(ORDER_A, QuantityModeView.STANDARD, 1L);
        applicationApi.putOrderQuantityMode(ORDER_B, QuantityModeView.STANDARD, 1L);
        applicationApi.putOrderQuantityMode(ORDER_C, QuantityModeView.STANDARD, 1L);
        applicationApi.releaseFailuresByOrder.put(
                ORDER_B,
                new RuntimeException(
                        "Insufficient production warehouse stock for material "
                                + MAT
                                + " in cell "
                                + CELL
                                + ": available=0, required=12.5"));
        queryApi.view =
                new OrderProductionView(
                        ORDER_A, OrderProductionViewStatus.IN_PRODUCTION, 1, 1, 0, 0, 0);
        viewModel.loadTree();
        ProductionOrderItemRef refA = new ProductionOrderItemRef(ORDER_A, ITEM_A1);
        ProductionOrderItemRef refB = new ProductionOrderItemRef(ORDER_B, ITEM_B2);
        ProductionOrderItemRef refC = new ProductionOrderItemRef(ORDER_C, ITEM_C3);
        viewModel.setItemSelected(refA, true);
        viewModel.setItemSelected(refB, true);
        viewModel.setItemSelected(refC, true);

        var quantityRows = viewModel.loadReleaseStep1().rows();
        List<ReleaseMaterialRow> materials = allocateDefaults(quantityRows);
        var result = viewModel.confirmReleases(quantityRows, materials);
        assertTrue(result.anySuccess());
        assertFalse(result.allSuccess());

        var kind = viewModel.applyReleaseResult(result, quantityRows);
        assertEquals(
                ProductionWorkbenchViewModel.ReleaseCompletionKind.PARTIAL_SUCCESS, kind);
        assertTrue(viewModel.errorMessageProperty().get().contains("частично"));
        assertTrue(viewModel.errorMessageProperty().get().contains("Недостаточно материала"));
        assertEquals("", viewModel.statusMessageProperty().get());
        assertFalse(viewModel.selectionModel().isItemSelected(refA));
        assertTrue(viewModel.selectionModel().isItemSelected(refB));
        assertTrue(viewModel.selectionModel().isItemSelected(refC));
    }

    @Test
    void insufficientStockMapperKeepsSpecificAvailableAndRequired() {
        String mapped =
                ProductionUiErrorMapper.text(
                        new RuntimeException(
                                "Insufficient production warehouse stock for material "
                                        + MAT
                                        + " in cell "
                                        + CELL
                                        + ": available=0, required=12.5"));
        assertTrue(mapped.contains("Недостаточно материала на производственном складе"));
        assertTrue(mapped.contains("Доступно: 0"));
        assertTrue(mapped.contains("Требуется: 12.5"));
        assertTrue(mapped.contains("Выпуск не выполнен"));
        assertFalse(mapped.contains("ReleaseProductsException"));
        assertFalse(mapped.contains(MAT.toString()));
    }

    @Test
    void openingWizardDoesNotMutateRelease() {
        seedOrders();
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
        viewModel.loadReleaseStep1();
        viewModel.checkReleaseReadiness(viewModel.loadReleaseStep1().rows());
        assertTrue(applicationApi.releaseProductCalls.isEmpty());
        assertTrue(applicationApi.releaseOrderCalls.isEmpty());
    }

    @Test
    void failedReleaseDoesNotAppearSuccessful() {
        seedOrders();
        applicationApi.releaseFailuresByOrder.put(
                ORDER_A,
                new RuntimeException(
                        "Insufficient production warehouse stock for material x in cell y:"
                                + " available=0, required=12.5"));
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
        var quantityRows = viewModel.loadReleaseStep1().rows();
        var result = viewModel.confirmReleases(quantityRows, allocateDefaults(quantityRows));
        viewModel.applyReleaseResult(result, quantityRows);
        assertFalse(result.allSuccess());
        assertFalse(viewModel.errorMessageProperty().get().isBlank());
        assertFalse(viewModel.statusMessageProperty().get().contains("Выпущено"));
        assertFalse(viewModel.errorMessageProperty().get().contains("Изделия выпущены"));
    }

    @Test
    void errorMapperMapsReleaseStockQuantityAndCancelled() {
        assertTrue(
                ProductionUiErrorMapper.text(
                                new RuntimeException(
                                        "Insufficient production warehouse stock for material x"
                                                + " in cell y: available=25, required=40"))
                        .contains("Доступно: 25"));
        assertEquals(
                ProductionUiErrorMapper.RELEASE_QUANTITY_CONFLICT,
                ProductionUiErrorMapper.text(
                        new RuntimeException(
                                "Release quantity exceeds active production quantity for item")));
        assertEquals(
                ProductionUiErrorMapper.RELEASE_CANCELLED,
                ProductionUiErrorMapper.text(
                        new RuntimeException(
                                "Release is allowed only when order Production View is"
                                        + " IN_PRODUCTION")));
    }

    private List<ReleaseMaterialRow> allocateDefaults(List<ReleaseQuantityRow> quantityRows) {
        return viewModel.prepareReleaseMaterialRows(quantityRows);
    }

    private void seedOrders() {
        seedOrder(ORDER_A, "4183", ITEM_A1, 10L, 6L, 4L);
        seedOrder(ORDER_B, "4184", ITEM_B2, 8L, 2L, 6L);
        seedOrder(ORDER_C, "4185", ITEM_C3, 5L, 3L, 2L);
        warehouseApi.cellsByWarehouse.put(
                applicationApi.productionWarehouseId,
                List.of(
                        new StorageCellView(
                                CELL, applicationApi.productionWarehouseId, "P-01", true)));
    }

    private void seedOrder(
            UUID orderId, String number, UUID itemId, long ordered, long released, long active) {
        worklistQuery.rows.add(
                ProductionWorkbenchUiTestSupport.worklistRow(orderId, number, "Клиент"));
        orderQuery.orders.put(
                OrderId.of(orderId), ProductionWorkbenchUiTestSupport.order(orderId, number));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(orderId, itemId, "1"));
        queryApi.listFacts.put(
                orderId,
                ProductionWorkbenchUiTestSupport.productionListFacts(
                        orderId,
                        OrderProductionViewStatus.IN_PRODUCTION,
                        ordered,
                        released,
                        active));
        queryApi.putItemState(
                state(
                        orderId,
                        itemId,
                        ItemProductionStateStatus.PARTIALLY_RELEASED,
                        ordered,
                        released,
                        active));
    }

    private static ItemProductionStateView state(
            UUID orderId,
            UUID itemId,
            ItemProductionStateStatus status,
            long ordered,
            long released,
            long active) {
        return new ItemProductionStateView(
                orderId,
                itemId,
                SPEC,
                status,
                ordered,
                ordered,
                active,
                released,
                Optional.empty(),
                Instant.parse("2026-01-01T00:00:00Z"),
                List.of());
    }
}
