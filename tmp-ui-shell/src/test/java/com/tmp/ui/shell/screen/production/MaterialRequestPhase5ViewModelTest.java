package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementDraftSummaryView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementLineView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementProductCoverageView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementProductSelectionView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementSourceItemView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementStatusView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementView;
import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaterialRequestPhase5ViewModelTest {

    private static final UUID ORDER_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORDER_B = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ORDER_C = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID ITEM_A1 = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID ITEM_B3 = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb3");
    private static final UUID ITEM_C1 = UUID.fromString("cccccccc-cccc-4ccc-8ccc-ccccccccccc1");
    private static final UUID SPEC = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID REQ_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID LINE_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID MAT_REF = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID WH = UUID.fromString("88888888-8888-4888-8888-888888888888");

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
    void requestMaterialsDisabledWithoutSelection() {
        seedThreeOrders();
        viewModel.loadTree();

        assertTrue(viewModel.canRequestMaterialsProperty().get());
        assertFalse(viewModel.requestMaterialsEnabledProperty().get());
    }

    @Test
    void requestMaterialsHiddenWithoutTransferPermission() {
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
                        new ProductionWorkbenchUiTestSupport.StubWarehouseApi(),
                        auth,
                        authentication);
        seedThreeOrders();
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);

        assertFalse(viewModel.canRequestMaterialsProperty().get());
        assertFalse(viewModel.requestMaterialsEnabledProperty().get());
    }

    @Test
    void oneSelectedItemOpensStep1WithStandardReadOnlyQuantity() {
        seedThreeOrders();
        applicationApi.putOrderQuantityMode(ORDER_A, QuantityModeView.STANDARD, 1L);
        applicationApi.putCoverage(coverage(ORDER_A, ITEM_A1, 10, 10, 0, 0, 10));
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);

        var loaded = viewModel.loadMaterialRequestStep1();

        assertTrue(loaded.ok());
        assertEquals(1, loaded.rows().size());
        MaterialRequestQuantityRow row = loaded.rows().getFirst();
        assertTrue(row.standardMode());
        assertEquals(10L, row.requestableProductQuantity());
        assertEquals(10L, row.requestedProductQuantity());
        assertEquals(10L, MaterialRequestDialogSupport.resolvedProductQuantityForPrepare(row));
    }

    @Test
    void flexibleDefaultsToRequestableAndPrepareSendsExplicitQuantity() {
        seedThreeOrders();
        applicationApi.putOrderQuantityMode(ORDER_B, QuantityModeView.FLEXIBLE, 1L);
        applicationApi.putCoverage(coverage(ORDER_B, ITEM_B3, 8, 8, 0, 0, 8));
        applicationApi.requirement = sampleDraft(List.of(source(ORDER_B, ITEM_B3, 3)), "40.000");
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_B, ITEM_B3), true);

        var loaded = viewModel.loadMaterialRequestStep1();
        assertTrue(loaded.ok());
        MaterialRequestQuantityRow row = loaded.rows().getFirst();
        assertFalse(row.standardMode());
        assertEquals(8L, row.requestedProductQuantity());
        row.setRequestedProductQuantity(3L);

        viewModel.prepareMaterialRequirement(List.of(row));

        MaterialRequirementProductSelectionView selection =
                applicationApi.lastPrepareSelections.getFirst();
        assertEquals(Optional.of(3L), selection.requestedProductQuantity());
    }

    @Test
    void mixedStandardFlexibleBuildsOnePrepareBatch() {
        seedThreeOrders();
        applicationApi.putOrderQuantityMode(ORDER_A, QuantityModeView.STANDARD, 1L);
        applicationApi.putOrderQuantityMode(ORDER_B, QuantityModeView.FLEXIBLE, 1L);
        applicationApi.putOrderQuantityMode(ORDER_C, QuantityModeView.STANDARD, 1L);
        applicationApi.putCoverage(coverage(ORDER_A, ITEM_A1, 10, 10, 0, 0, 10));
        applicationApi.putCoverage(coverage(ORDER_B, ITEM_B3, 8, 8, 0, 0, 8));
        applicationApi.putCoverage(coverage(ORDER_C, ITEM_C1, 5, 5, 0, 0, 5));
        applicationApi.requirement =
                sampleDraft(
                        List.of(
                                source(ORDER_A, ITEM_A1, 10),
                                source(ORDER_B, ITEM_B3, 3),
                                source(ORDER_C, ITEM_C1, 5)),
                        "72.000");
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_B, ITEM_B3), true);
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_C, ITEM_C1), true);

        var loaded = viewModel.loadMaterialRequestStep1();
        assertTrue(loaded.ok());
        assertEquals(3, loaded.rows().size());
        loaded.rows().get(1).setRequestedProductQuantity(3L);

        MaterialRequirementView prepared = viewModel.prepareMaterialRequirement(loaded.rows());

        assertEquals(3, applicationApi.lastPrepareSelections.size());
        assertEquals(
                Optional.empty(),
                applicationApi.lastPrepareSelections.get(0).requestedProductQuantity());
        assertEquals(
                Optional.of(3L),
                applicationApi.lastPrepareSelections.get(1).requestedProductQuantity());
        assertEquals(
                Optional.empty(),
                applicationApi.lastPrepareSelections.get(2).requestedProductQuantity());
        assertEquals(3, prepared.sourceItems().size());
        assertEquals(REQ_ID, prepared.requirementId());
        assertEquals(new BigDecimal("72.000"), prepared.lines().getFirst().quantity());
    }

    @Test
    void invalidSelectionIsNotSilentlyReduced() {
        seedThreeOrders();
        applicationApi.putCoverage(coverage(ORDER_A, ITEM_A1, 10, 10, 0, 10, 0));
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);

        var loaded = viewModel.loadMaterialRequestStep1();

        assertFalse(loaded.ok());
        assertTrue(loaded.validationMessage().contains("уже запрошено"));
        assertTrue(loaded.rows().isEmpty());
    }

    @Test
    void materialEditDoesNotChangeProductCoverageSourceQuantities() {
        applicationApi.requirement = sampleDraft(List.of(source(ORDER_A, ITEM_A1, 10)), "40.000");

        MaterialRequirementView edited =
                viewModel.changeMaterialRequirementLineQuantity(
                        REQ_ID, LINE_ID, new BigDecimal("42.000"), 0L);

        assertEquals(new BigDecimal("42.000"), edited.lines().getFirst().quantity());
        assertEquals(10L, edited.sourceItems().getFirst().requestedProductQuantity());
    }

    @Test
    void draftListReopensPersistedRequirement() {
        applicationApi.requirement = sampleDraft(List.of(source(ORDER_A, ITEM_A1, 10)), "40.000");

        var drafts = viewModel.listMaterialRequirementDrafts();
        assertEquals(1, drafts.size());
        assertEquals(REQ_ID, drafts.getFirst().requirementId());

        Optional<MaterialRequirementView> reopened = viewModel.getMaterialRequirement(REQ_ID);
        assertTrue(reopened.isPresent());
        assertEquals(0L, reopened.get().version());
        assertEquals(10L, reopened.get().sourceItems().getFirst().requestedProductQuantity());
        assertEquals(new BigDecimal("40.000"), reopened.get().lines().getFirst().quantity());
    }

    @Test
    void successfulSubmitClearsSelectionOfSubmittedItems() {
        seedThreeOrders();
        applicationApi.putCoverage(coverage(ORDER_A, ITEM_A1, 10, 10, 0, 0, 10));
        applicationApi.requirement = sampleDraft(List.of(source(ORDER_A, ITEM_A1, 10)), "40.000");
        viewModel.loadTree();
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
        viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_B, ITEM_B3), true);

        viewModel.afterSuccessfulMaterialSubmit(applicationApi.requirement);

        assertFalse(
                viewModel
                        .selectionModel()
                        .isItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1)));
        assertTrue(
                viewModel
                        .selectionModel()
                        .isItemSelected(new ProductionOrderItemRef(ORDER_B, ITEM_B3)));
        assertEquals(
                ProductionUiErrorMapper.MATERIAL_SUBMIT_SUCCESS,
                viewModel.statusMessageProperty().get());
    }

    @Test
    void step1ValidationRejectsZeroFlexibleQuantity() {
        MaterialRequestQuantityRow row =
                new MaterialRequestQuantityRow(
                        ORDER_B,
                        ITEM_B3,
                        "4184",
                        "Поз. 3",
                        "Дверь",
                        QuantityModeView.FLEXIBLE,
                        1,
                        1);
        row.setRequestedProductQuantity(0L);

        Optional<String> error = MaterialRequestDialogSupport.validateStep1Quantities(List.of(row));
        assertTrue(error.isPresent());
        assertTrue(error.get().contains("от 1 до 1"));
    }

    @Test
    void step1ValidationRejectsAboveRequestableFlexibleQuantity() {
        MaterialRequestQuantityRow row =
                new MaterialRequestQuantityRow(
                        ORDER_B,
                        ITEM_B3,
                        "4184",
                        "Поз. 3",
                        "Дверь",
                        QuantityModeView.FLEXIBLE,
                        1,
                        1);
        row.setRequestedProductQuantity(2L);

        Optional<String> error = MaterialRequestDialogSupport.validateStep1Quantities(List.of(row));
        assertTrue(error.isPresent());
        assertTrue(error.get().contains("от 1 до 1"));
    }

    @Test
    void step1ValidationAcceptsExactRequestableFlexibleQuantity() {
        MaterialRequestQuantityRow row =
                new MaterialRequestQuantityRow(
                        ORDER_B,
                        ITEM_B3,
                        "4184",
                        "Поз. 3",
                        "Дверь",
                        QuantityModeView.FLEXIBLE,
                        1,
                        1);
        row.setRequestedProductQuantity(1L);

        assertTrue(MaterialRequestDialogSupport.validateStep1Quantities(List.of(row)).isEmpty());
    }

    @Test
    void errorMapperMapsCoverageConflictShortageAndModeRace() {
        assertEquals(
                ProductionUiErrorMapper.MATERIAL_COVERAGE_CONFLICT,
                ProductionUiErrorMapper.text(
                        new RuntimeException("MaterialRequirementCoverageConflict: excess")));
        assertEquals(
                ProductionUiErrorMapper.MATERIAL_SHORTAGE,
                ProductionUiErrorMapper.text(
                        new RuntimeException("MaterialRequirementShortage: no stock")));
        assertEquals(
                ProductionUiErrorMapper.QUANTITY_MODE_CHANGED_FOR_REQUEST,
                ProductionUiErrorMapper.text(
                        new RuntimeException(
                                "MaterialRequirementSelectionException: STANDARD mode must not"
                                        + " supply requestedProductQuantity")));
        assertEquals(
                ProductionUiErrorMapper.MATERIAL_COVERAGE_CHANGED,
                ProductionUiErrorMapper.text(
                        new RuntimeException(
                                "MaterialRequirementSelectionException: requestedProductQuantity"
                                        + " exceeds requestable")));
        assertEquals(ProductionUiErrorMapper.ACCESS_DENIED, "Недостаточно прав для этого действия");
    }

    @Test
    void draftListCaptionNeverExposesUuid() {
        MaterialRequirementDraftSummaryView draft =
                new MaterialRequirementDraftSummaryView(
                        REQ_ID, Instant.parse("2026-10-01T07:15:00Z"), 3, 2);
        String caption = MaterialRequestDialogSupport.draftListCaption(draft);
        assertFalse(caption.toLowerCase().contains("uuid"));
        assertFalse(caption.contains(REQ_ID.toString()));
        assertTrue(caption.contains("3"));
        assertTrue(caption.contains("2"));
    }

    private void seedThreeOrders() {
        worklistQuery.rows.add(
                ProductionWorkbenchUiTestSupport.worklistRow(ORDER_A, "4183", "Cust A"));
        worklistQuery.rows.add(
                ProductionWorkbenchUiTestSupport.worklistRow(ORDER_B, "4184", "Cust B"));
        worklistQuery.rows.add(
                ProductionWorkbenchUiTestSupport.worklistRow(ORDER_C, "4187", "Cust C"));
        orderQuery.orders.put(OrderId.of(ORDER_A), ProductionWorkbenchUiTestSupport.order(ORDER_A, "4183"));
        orderQuery.orders.put(OrderId.of(ORDER_B), ProductionWorkbenchUiTestSupport.order(ORDER_B, "4184"));
        orderQuery.orders.put(OrderId.of(ORDER_C), ProductionWorkbenchUiTestSupport.order(ORDER_C, "4187"));
        queryApi.listFacts.put(
                ORDER_A,
                ProductionWorkbenchUiTestSupport.productionListFacts(
                        ORDER_A, OrderProductionViewStatus.IN_PRODUCTION, 10, 0, 10));
        queryApi.listFacts.put(
                ORDER_B,
                ProductionWorkbenchUiTestSupport.productionListFacts(
                        ORDER_B, OrderProductionViewStatus.IN_PRODUCTION, 8, 0, 8));
        queryApi.listFacts.put(
                ORDER_C,
                ProductionWorkbenchUiTestSupport.productionListFacts(
                        ORDER_C, OrderProductionViewStatus.IN_PRODUCTION, 5, 0, 5));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_A, ITEM_A1, "1"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_B, ITEM_B3, "3"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_C, ITEM_C1, "1"));
        putState(ORDER_A, ITEM_A1, 10, 10, 0);
        putState(ORDER_B, ITEM_B3, 8, 8, 0);
        putState(ORDER_C, ITEM_C1, 5, 5, 0);
    }

    private void putState(UUID orderId, UUID itemId, long ordered, long active, long released) {
        queryApi.putItemState(
                new ItemProductionStateView(
                        orderId,
                        itemId,
                        SPEC,
                        ItemProductionStateStatus.IN_PRODUCTION,
                        ordered,
                        ordered,
                        active,
                        released,
                        Optional.empty(),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        List.of()));
    }

    private static MaterialRequirementProductCoverageView coverage(
            UUID orderId,
            UUID itemId,
            long ordered,
            long active,
            long released,
            long submitted,
            long requestable) {
        return new MaterialRequirementProductCoverageView(
                orderId,
                itemId,
                ordered,
                active,
                released,
                submitted,
                Math.max(0L, submitted - released),
                requestable);
    }

    private static MaterialRequirementSourceItemView source(UUID orderId, UUID itemId, long qty) {
        return new MaterialRequirementSourceItemView(orderId, itemId, qty);
    }

    private static MaterialRequirementView sampleDraft(
            List<MaterialRequirementSourceItemView> sources, String quantity) {
        return new MaterialRequirementView(
                REQ_ID,
                sources,
                WH,
                Instant.parse("2026-10-01T07:15:00Z"),
                Instant.parse("2026-10-01T07:15:00Z"),
                0L,
                MaterialRequirementStatusView.DRAFT,
                Optional.empty(),
                Optional.empty(),
                List.of(
                        new MaterialRequirementLineView(
                                LINE_ID,
                                MAT_REF,
                                "101.208",
                                "Профиль",
                                "Белый",
                                "м",
                                new BigDecimal(quantity),
                                sources.stream()
                                        .map(MaterialRequirementSourceItemView::sourceOrderItemId)
                                        .toList())));
    }
}
