package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.MaterialReferenceEntry;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReleaseMaterialPlanBuilderTest {

    private static final Instant T0 = Instant.parse("2026-08-23T10:00:00Z");

    private final ReleaseMaterialPlanBuilder builder = new ReleaseMaterialPlanBuilder();

    @Test
    void aggregatesAfterPerSpecificationLineCalculation() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        ProductionItemState state =
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemId, specId, T0),
                        ProductionQuantity.positive(3),
                        T0);

        UUID materialRef = UUID.randomUUID();
        List<ResolvedMaterialLine> specLines =
                List.of(
                        line("MAT-A", new BigDecimal("0.5")),
                        line("MAT-A", new BigDecimal("0.5")));
        List<MaterialReferenceEntry> catalog =
                List.of(entry(materialRef, "MAT-A", "", "шт."));

        var planned =
                builder.buildPlannedLines(state, 1, specLines, catalog).getFirst();
        assertEquals(new BigDecimal("0.333334"), planned.plannedQuantity());

        BigDecimal aggregateFirst =
                new PartialReleaseMaterialPlanCalculator()
                        .calculate(
                                new PartialReleaseMaterialPlanCalculator.Input(
                                        new BigDecimal("1"), 3, 0, 1))
                        .planCurrent();
        assertEquals(new BigDecimal("0.333333"), aggregateFirst);
    }

    private static ResolvedMaterialLine line(String code, BigDecimal q) {
        return new ResolvedMaterialLine(code, code, "", null, q, "шт.");
    }

    private static MaterialReferenceEntry entry(
            UUID id, String article, String color, String uom) {
        return new MaterialReferenceEntry(id, article, article, color, "", uom);
    }
}
