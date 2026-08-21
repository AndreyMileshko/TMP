package com.tmp.production.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaterialTransferTemplateTest {

    private static final Instant T0 = Instant.parse("2026-08-21T04:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-21T05:00:00Z");
    private static final UUID MAIN = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID PROD = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Test
    void editChangesRequestedQuantityOnly() {
        MaterialTransferTemplateLine line = sampleLine(bd(10));
        MaterialTransferTemplate template =
                MaterialTransferTemplate.create(
                        SourceOrderId.generate(), MAIN, PROD, T0, List.of(line));

        MaterialTransferTemplate edited =
                template.changeRequestedQuantity(line.lineId(), bd(8), T1);

        MaterialTransferTemplateLine result = edited.lines().getFirst();
        assertEquals(0, result.recommendedQuantity().compareTo(bd(10)));
        assertEquals(0, result.requestedQuantity().compareTo(bd(8)));
        assertTrue(result.included());
    }

    @Test
    void excludeUsesIncludedFlagNotNegativeQuantity() {
        MaterialTransferTemplateLine line = sampleLine(bd(10));
        MaterialTransferTemplate template =
                MaterialTransferTemplate.create(
                        SourceOrderId.generate(), MAIN, PROD, T0, List.of(line));

        MaterialTransferTemplate excluded = template.excludeLine(line.lineId(), T1);

        MaterialTransferTemplateLine result = excluded.lines().getFirst();
        assertFalse(result.included());
        assertEquals(0, result.requestedQuantity().compareTo(bd(10)));
        assertEquals(0, result.recommendedQuantity().compareTo(bd(10)));
        assertTrue(excluded.includedTransferLines().isEmpty());
    }

    @Test
    void restoreReincludesLine() {
        MaterialTransferTemplateLine line = sampleLine(bd(10));
        MaterialTransferTemplate template =
                MaterialTransferTemplate.create(
                        SourceOrderId.generate(), MAIN, PROD, T0, List.of(line));

        MaterialTransferTemplate restored =
                template.excludeLine(line.lineId(), T1).restoreLine(line.lineId(), T1);

        assertTrue(restored.lines().getFirst().included());
        assertEquals(1, restored.includedTransferLines().size());
    }

    @Test
    void rejectsZeroOrNegativeRequestedQuantityForIncludedLine() {
        MaterialTransferTemplateLine line = sampleLine(bd(10));
        MaterialTransferTemplate template =
                MaterialTransferTemplate.create(
                        SourceOrderId.generate(), MAIN, PROD, T0, List.of(line));

        assertThrows(
                IllegalArgumentException.class,
                () -> template.changeRequestedQuantity(line.lineId(), BigDecimal.ZERO, T1));
        assertThrows(
                IllegalArgumentException.class,
                () -> template.changeRequestedQuantity(line.lineId(), bd(-1), T1));
    }

    @Test
    void rejectsSameSourceAndDestinationWarehouses() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MaterialTransferTemplate.create(
                                SourceOrderId.generate(), MAIN, MAIN, T0, List.of()));
    }

    @Test
    void confirmMarksTemplateConfirmed() {
        MaterialTransferTemplateLine line = sampleLine(bd(10));
        MaterialTransferTemplate template =
                MaterialTransferTemplate.create(
                        SourceOrderId.generate(), MAIN, PROD, T0, List.of(line));

        MaterialTransferTemplate confirmed = template.confirm(T1);

        assertEquals(MaterialTransferTemplateStatus.CONFIRMED, confirmed.status());
        assertEquals(Optional.of(T1), confirmed.confirmedAt());
        assertThrows(
                MaterialTransferTemplateNotEditableException.class,
                () -> confirmed.changeRequestedQuantity(line.lineId(), bd(8), T1));
        assertThrows(
                MaterialTransferTemplateNotEditableException.class,
                () -> confirmed.excludeLine(line.lineId(), T1));
        assertThrows(
                MaterialTransferTemplateNotEditableException.class,
                () -> confirmed.restoreLine(line.lineId(), T1));
    }

    private static MaterialTransferTemplateLine sampleLine(BigDecimal quantity) {
        return MaterialTransferTemplateLine.create(
                MaterialReferenceId.generate(),
                "MAT-1",
                "Material",
                "WHITE",
                "PCS",
                quantity,
                MaterialPlanningSource.SPECIFICATION,
                null,
                CuttingLinkStatus.NONE,
                List.of(),
                Set.of(SourceOrderItemId.generate()),
                quantity,
                bd(20),
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    private static BigDecimal bd(long value) {
        return BigDecimal.valueOf(value);
    }
}
