package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.screen.production.ReleaseConfirmPresentation.ConfirmMaterialAggregate;
import com.tmp.ui.shell.screen.production.ReleaseConfirmPresentation.ConfirmSummaryStats;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ReleaseConfirmPresentationTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    private static final UUID ORDER_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORDER_B = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ITEM_A1 = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID ITEM_A2 = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2");
    private static final UUID ITEM_B1 = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1");
    private static final UUID MAT_1 = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID MAT_2 = UUID.fromString("88888888-8888-4888-8888-888888888888");

    @Test
    void oneOrderOneMaterialSummaryAndAggregate() {
        ReleaseQuantityRow qty = quantity(ORDER_A, ITEM_A1, "Заказ №1", 2L);
        ReleaseMaterialRow material =
                material(ORDER_A, ITEM_A1, MAT_1, "Заказ №1", "ART-1", "Стекло", "м", "10", "10");

        ConfirmSummaryStats stats =
                ReleaseConfirmPresentation.summarize(List.of(qty), List.of(material));
        assertEquals(1, stats.orderCount());
        assertEquals(1, stats.positionCount());
        assertEquals(2L, stats.productCount());
        assertEquals(1, stats.uniqueMaterialCount());
        assertEquals(0, stats.deviationCount());

        List<ConfirmMaterialAggregate> aggregates =
                ReleaseConfirmPresentation.aggregateMaterialsForDisplay(List.of(material));
        assertEquals(1, aggregates.size());
        assertEquals("ART-1", aggregates.getFirst().materialCode());
        assertEquals("Стекло", aggregates.getFirst().materialName());
        assertEquals("м", aggregates.getFirst().unitOfMeasure());
        assertEquals("10", aggregates.getFirst().actualLabel());
        assertFalse(aggregates.getFirst().hasDeviation());
    }

    @Test
    void aggregatesIdenticalMaterialWithinOrderAndPreservesPostingRows() {
        ReleaseMaterialRow row1 =
                material(ORDER_A, ITEM_A1, MAT_1, "Заказ №1", "ART-1", "Стекло", "м", "5", "4");
        ReleaseMaterialRow row2 =
                material(ORDER_A, ITEM_A2, MAT_1, "Заказ №1", "ART-1", "Стекло", "м", "7", "8");
        List<ReleaseMaterialRow> postingRows = List.of(row1, row2);

        List<ConfirmMaterialAggregate> aggregates =
                ReleaseConfirmPresentation.aggregateMaterialsForDisplay(postingRows);
        assertEquals(1, aggregates.size());
        assertEquals(0, aggregates.getFirst().plannedQuantity().compareTo(new java.math.BigDecimal("12")));
        assertEquals(0, aggregates.getFirst().actualQuantity().compareTo(new java.math.BigDecimal("12")));
        assertFalse(aggregates.getFirst().hasDeviation());

        assertEquals(2, postingRows.size());
        assertEquals("4", postingRows.get(0).actualQuantity());
        assertEquals("8", postingRows.get(1).actualQuantity());
    }

    @Test
    void doesNotAggregateIdenticalMaterialAcrossOrders() {
        ReleaseMaterialRow a =
                material(ORDER_A, ITEM_A1, MAT_1, "Заказ №1", "ART-1", "Стекло", "м", "5", "5");
        ReleaseMaterialRow b =
                material(ORDER_B, ITEM_B1, MAT_1, "Заказ №2", "ART-1", "Стекло", "м", "5", "5");

        List<ConfirmMaterialAggregate> aggregates =
                ReleaseConfirmPresentation.aggregateMaterialsForDisplay(List.of(a, b));
        assertEquals(2, aggregates.size());
        assertEquals(ORDER_A, aggregates.get(0).sourceOrderId());
        assertEquals(ORDER_B, aggregates.get(1).sourceOrderId());
    }

    @Test
    void deviationUsesAggregateCompareTo() {
        ReleaseMaterialRow row1 =
                material(ORDER_A, ITEM_A1, MAT_1, "Заказ №1", "ART-1", "Стекло", "м", "5", "5");
        ReleaseMaterialRow row2 =
                material(ORDER_A, ITEM_A2, MAT_1, "Заказ №1", "ART-1", "Стекло", "м", "5", "6");

        ConfirmSummaryStats stats =
                ReleaseConfirmPresentation.summarize(
                        List.of(quantity(ORDER_A, ITEM_A1, "Заказ №1", 1L)),
                        List.of(row1, row2));
        assertEquals(1, stats.uniqueMaterialCount());
        assertEquals(1, stats.deviationCount());

        ConfirmMaterialAggregate aggregate =
                ReleaseConfirmPresentation.aggregateMaterialsForDisplay(List.of(row1, row2))
                        .getFirst();
        assertTrue(aggregate.hasDeviation());
        assertEquals(ReleaseConfirmPresentation.DEVIATION_MARK, aggregate.deviationLabel());
    }

    @Test
    void searchFiltersDisplayOnlyByArticleAndName() {
        ConfirmMaterialAggregate glass =
                new ConfirmMaterialAggregate(
                        ORDER_A,
                        "Заказ №1",
                        MAT_1,
                        "ART-GLASS",
                        "Стекло",
                        "м",
                        new java.math.BigDecimal("1"),
                        new java.math.BigDecimal("1"));
        ConfirmMaterialAggregate seal =
                new ConfirmMaterialAggregate(
                        ORDER_A,
                        "Заказ №1",
                        MAT_2,
                        "ART-SEAL",
                        "Уплотнитель",
                        "шт",
                        new java.math.BigDecimal("2"),
                        new java.math.BigDecimal("2"));

        assertEquals(
                1,
                ReleaseConfirmPresentation.filterAggregates(List.of(glass, seal), "glass").size());
        assertEquals(
                1,
                ReleaseConfirmPresentation.filterAggregates(List.of(glass, seal), "УПЛОТ")
                        .size());
        assertEquals(
                2,
                ReleaseConfirmPresentation.filterAggregates(List.of(glass, seal), "").size());
    }

    @Test
    void manyMaterialsSummaryCountsUniqueAggregates() {
        List<ReleaseMaterialRow> materials = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            UUID mat = UUID.fromString(String.format("77777777-7777-4777-8777-%012d", i));
            materials.add(
                    material(
                            ORDER_A,
                            ITEM_A1,
                            mat,
                            "Заказ №1",
                            "ART-" + i,
                            "Материал " + i,
                            "м",
                            "1",
                            "1"));
        }
        ConfirmSummaryStats stats =
                ReleaseConfirmPresentation.summarize(
                        List.of(quantity(ORDER_A, ITEM_A1, "Заказ №1", 1L)), materials);
        assertEquals(120, stats.uniqueMaterialCount());
        assertEquals(0, stats.deviationCount());
        assertEquals(120, ReleaseConfirmPresentation.aggregateMaterialsForDisplay(materials).size());
    }

    @Test
    void multiOrderSummaryAndGrouping() {
        List<ReleaseQuantityRow> quantities =
                List.of(
                        quantity(ORDER_A, ITEM_A1, "Заказ №1", 3L),
                        quantity(ORDER_B, ITEM_B1, "Заказ №2", 5L));
        List<ReleaseMaterialRow> materials =
                List.of(
                        material(
                                ORDER_A, ITEM_A1, MAT_1, "Заказ №1", "A1", "Стекло", "м", "1", "1"),
                        material(
                                ORDER_B, ITEM_B1, MAT_2, "Заказ №2", "B1", "Профиль", "м", "2", "3"));

        ConfirmSummaryStats stats = ReleaseConfirmPresentation.summarize(quantities, materials);
        assertEquals(2, stats.orderCount());
        assertEquals(2, stats.positionCount());
        assertEquals(8L, stats.productCount());
        assertEquals(2, stats.uniqueMaterialCount());
        assertEquals(1, stats.deviationCount());

        String content = ReleaseConfirmPresentation.confirmSummaryContent(quantities, materials);
        assertTrue(content.contains("Заказов: 2"));
        assertTrue(content.contains(ProductionUiErrorMapper.RELEASE_CONFIRM_STOCK_NOTE));
        assertFalse(content.contains("Материалы проверены"));
    }

    @Test
    void zeroActualIsValidAndCountsAsDeviationWhenPlanNonZero() {
        ReleaseMaterialRow row =
                material(ORDER_A, ITEM_A1, MAT_1, "Заказ №1", "ART-1", "Стекло", "м", "5", "0");
        ConfirmMaterialAggregate aggregate =
                ReleaseConfirmPresentation.aggregateMaterialsForDisplay(List.of(row)).getFirst();
        assertTrue(aggregate.hasDeviation());
        assertEquals("0", aggregate.actualLabel());
    }

    @Test
    void differentUomDoesNotMerge() {
        ReleaseMaterialRow meters =
                material(ORDER_A, ITEM_A1, MAT_1, "Заказ №1", "ART-1", "Стекло", "м", "5", "5");
        ReleaseMaterialRow pieces =
                material(ORDER_A, ITEM_A2, MAT_1, "Заказ №1", "ART-1", "Стекло", "шт", "2", "2");
        assertEquals(
                2,
                ReleaseConfirmPresentation.aggregateMaterialsForDisplay(List.of(meters, pieces))
                        .size());
    }

    @Test
    void tooltipHelperPreservesFullRequiredQuantityText() {
        String full = "14.712 (12 × 1226 мм)";
        assertEquals(full, ProductionTableCells.fullTextTooltip(full).getText());
        assertEquals(160, ProductionTableCells.REQUIRED_QUANTITY_MIN_WIDTH);
    }

    private static ReleaseQuantityRow quantity(
            UUID orderId, UUID itemId, String orderLabel, long releaseQty) {
        return new ReleaseQuantityRow(
                orderId,
                itemId,
                orderLabel,
                "1",
                "Изделие",
                QuantityModeView.FLEXIBLE,
                10L,
                0L,
                10L,
                releaseQty);
    }

    private static ReleaseMaterialRow material(
            UUID orderId,
            UUID itemId,
            UUID materialId,
            String orderLabel,
            String code,
            String name,
            String uom,
            String planned,
            String actual) {
        return new ReleaseMaterialRow(
                orderId, itemId, materialId, orderLabel, code, name, uom, planned, actual);
    }
}
