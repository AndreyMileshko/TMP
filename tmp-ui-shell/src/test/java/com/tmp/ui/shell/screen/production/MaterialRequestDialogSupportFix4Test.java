package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementLineProductionStockView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementLineView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementSourceItemView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementStatusView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementView;
import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaterialRequestDialogSupportFix4Test {

    private static final UUID REQ_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID LINE_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID ORDER_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID ITEM_ID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    private static final UUID WH_ID = UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");

    @Test
    void test002RowShowsStockRequiredAndFullRequestQuantity() {
        MaterialRequirementView requirement = draft(bd("230"), bd("230"));
        List<MaterialRequirementLineProductionStockView> stock =
                List.of(new MaterialRequirementLineProductionStockView(LINE_ID, bd("20")));

        MaterialRequestDraftLineRow row =
                MaterialRequestDialogSupport.toLineRows(requirement, stock).getFirst();

        assertEquals("230", strip(row.requiredQuantityLabel()));
        assertEquals("20", strip(row.onProductionAvailableLabel()));
        assertEquals("230", strip(row.quantityToRequestLabel()));
        assertEquals(0, bd("230").compareTo(row.quantity()));
        assertFalse(bd("210").compareTo(row.quantity()) == 0);
    }

    @Test
    void existingStockNotSubtractedFromToRequest() {
        MaterialRequirementView requirement = draft(bd("230"), bd("230"));
        List<MaterialRequirementLineProductionStockView> stock =
                List.of(new MaterialRequirementLineProductionStockView(LINE_ID, bd("6")));

        MaterialRequestDraftLineRow row =
                MaterialRequestDialogSupport.toLineRows(requirement, stock).getFirst();

        assertEquals("6", strip(row.onProductionAvailableLabel()));
        assertEquals("230", strip(row.quantityToRequestLabel()));
    }

    @Test
    void zeroStockShowsZero() {
        MaterialRequestDraftLineRow row =
                MaterialRequestDialogSupport.toLineRows(
                                draft(bd("10"), bd("10")),
                                List.of(
                                        new MaterialRequirementLineProductionStockView(
                                                LINE_ID, BigDecimal.ZERO)))
                        .getFirst();

        assertEquals("0", strip(row.onProductionAvailableLabel()));
    }

    @Test
    void unmatchedAndAmbiguousShowDash() {
        MaterialRequestDraftLineRow unmatched =
                MaterialRequestDialogSupport.toLineRows(
                                draft(bd("10"), bd("10")),
                                List.of(
                                        new MaterialRequirementLineProductionStockView(
                                                LINE_ID, null)))
                        .getFirst();
        assertEquals(MaterialRequestDraftLineRow.UNRESOLVED_STOCK_DISPLAY, unmatched.onProductionAvailableLabel());
        assertNull(unmatched.onProductionAvailable());
        assertEquals("10", strip(unmatched.quantityToRequestLabel()));
    }

    @Test
    void flexibleRecalcKeepsFullDerivedMaterialQuantity() {
        MaterialRequestQuantityRow productRow =
                new MaterialRequestQuantityRow(
                        ORDER_ID,
                        ITEM_ID,
                        "4183",
                        "Poz. 1",
                        "Okno",
                        QuantityModeView.FLEXIBLE,
                        115L,
                        10L);
        assertEquals(10L, MaterialRequestDialogSupport.resolvedProductQuantityForPrepare(productRow));

        MaterialRequestDraftLineRow materialRow =
                MaterialRequestDialogSupport.toLineRows(
                                draft(bd("20"), bd("20")),
                                List.of(
                                        new MaterialRequirementLineProductionStockView(
                                                LINE_ID, bd("6"))))
                        .getFirst();
        assertEquals("6", strip(materialRow.onProductionAvailableLabel()));
        assertEquals("20", strip(materialRow.quantityToRequestLabel()));
    }

    @Test
    void standardModeUsesFullRequestableProductQuantity() {
        MaterialRequestQuantityRow row =
                new MaterialRequestQuantityRow(
                        ORDER_ID,
                        ITEM_ID,
                        "4183",
                        "Poz. 1",
                        "Okno",
                        QuantityModeView.STANDARD,
                        115L,
                        1L);
        assertEquals(115L, MaterialRequestDialogSupport.resolvedProductQuantityForPrepare(row));
    }

    @Test
    void requestQuantityIsEditableAndValidatedAgainstRequired() {
        assertTrue(
                java.util.Arrays.stream(MaterialRequestDraftLineRow.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().equals("setQuantity")));
        assertEquals(MaterialRequestDialogSupport.COLUMN_REQUIRED, "\u0422\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044f");
        assertEquals(
                MaterialRequestDialogSupport.COLUMN_ON_PRODUCTION, "\u041d\u0430 \u043f\u0440\u043e\u0438\u0437\u0432\u043e\u0434\u0441\u0442\u0432\u0435");
        assertEquals(MaterialRequestDialogSupport.COLUMN_TO_REQUEST, "\u041a \u0437\u0430\u043f\u0440\u043e\u0441\u0443");
        assertTrue(
                MaterialRequestDialogSupport.STOCK_INFO_HINT.contains(
                        "\u043d\u0435 \u0443\u043c\u0435\u043d\u044c\u0448\u0430\u0435\u0442 \u043a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e \u043a \u0437\u0430\u043f\u0440\u043e\u0441\u0443"));

        assertTrue(
                MaterialRequestDialogSupport.validateRequestQuantity(BigDecimal.ZERO, bd("20"))
                        .isEmpty());
        assertTrue(
                MaterialRequestDialogSupport.validateRequestQuantity(bd("10.25"), bd("12.5"))
                        .isEmpty());
        assertEquals(
                Optional.of(MaterialRequestDialogSupport.REQUEST_QUANTITY_RANGE_MESSAGE),
                MaterialRequestDialogSupport.validateRequestQuantity(bd("21"), bd("20")));
        assertEquals(
                Optional.of(MaterialRequestDialogSupport.REQUEST_QUANTITY_RANGE_MESSAGE),
                MaterialRequestDialogSupport.validateRequestQuantity(bd("-1"), bd("20")));
    }

    @Test
    void reducedRequestKeepsRequiredDistinct() {
        MaterialRequestDraftLineRow row =
                MaterialRequestDialogSupport.toLineRows(
                                draft(bd("230"), bd("210")),
                                List.of(
                                        new MaterialRequirementLineProductionStockView(
                                                LINE_ID, bd("20"))))
                        .getFirst();
        assertEquals("230", strip(row.requiredQuantityLabel()));
        assertEquals("210", strip(row.quantityToRequestLabel()));
        row.setQuantity(BigDecimal.ZERO);
        assertEquals("0", strip(row.quantityToRequestLabel()));
        assertEquals("230", strip(row.requiredQuantityLabel()));
    }

    private static MaterialRequirementView draft(BigDecimal required, BigDecimal request) {
        return new MaterialRequirementView(
                REQ_ID,
                List.of(new MaterialRequirementSourceItemView(ORDER_ID, ITEM_ID, 115L)),
                WH_ID,
                Instant.parse("2026-10-07T00:00:00Z"),
                Instant.parse("2026-10-07T00:00:00Z"),
                0L,
                MaterialRequirementStatusView.DRAFT,
                Optional.empty(),
                Optional.empty(),
                List.of(
                        new MaterialRequirementLineView(
                                LINE_ID,
                                null,
                                "MAT-001",
                                "Material",
                                "White",
                                "pcs",
                                null,
                                required,
                                request,
                                List.of(ITEM_ID))));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static String strip(String formatted) {
        return formatted.replace(" ", "").replace(",", ".");
    }
}