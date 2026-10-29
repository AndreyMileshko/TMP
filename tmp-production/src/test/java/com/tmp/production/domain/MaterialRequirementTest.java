package com.tmp.production.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaterialRequirementTest {

    private static final Instant T0 = Instant.parse("2026-09-09T04:00:00Z");
    private static final Instant T1 = Instant.parse("2026-09-09T05:00:00Z");
    private static final UUID PROD = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Test
    void createStartsAtVersionZeroDraft() {
        MaterialRequirementLine line = sampleLine(bd(28));
        MaterialRequirement requirement =
                MaterialRequirement.create(PROD, T0, sampleSourceItems(line), List.of(line));

        assertEquals(0L, requirement.version());
        assertEquals(MaterialRequirementStatus.DRAFT, requirement.status());
        assertEquals(T0, requirement.createdAt());
        assertEquals(T0, requirement.updatedAt());
        assertEquals(1, requirement.sourceItems().size());
    }

    @Test
    void changeLineQuantityKeepsDomainVersionAndUpdatesQuantity() {
        MaterialRequirementLine line = sampleLine(bd(28));
        MaterialRequirement requirement =
                MaterialRequirement.create(PROD, T0, sampleSourceItems(line), List.of(line));

        MaterialRequirement edited =
                requirement.changeLineQuantity(line.lineId(), bd(25), T1);

        assertEquals(0L, edited.version());
        assertEquals(T1, edited.updatedAt());
        assertEquals(0, edited.lines().getFirst().quantity().compareTo(bd(25)));
        assertEquals(0, edited.lines().getFirst().requiredQuantity().compareTo(bd(28)));
    }

    @Test
    void changeLineQuantitiesUpdatesAllProvidedLinesAtomically() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemA = SourceOrderItemId.generate();
        SourceOrderItemId itemB = SourceOrderItemId.generate();
        MaterialRequirementLine lineA =
                MaterialRequirementLine.create(
                        MaterialReferenceId.generate(),
                        "MAT-1",
                        "Material 1",
                        "WHITE",
                        "PCS",
                        null,
                        bd(10),
                        List.of(MaterialRequirementLineContribution.of(orderId, itemA, bd(10))));
        MaterialRequirementLine lineB =
                MaterialRequirementLine.create(
                        MaterialReferenceId.generate(),
                        "MAT-2",
                        "Material 2",
                        "BLACK",
                        "PCS",
                        null,
                        bd(20),
                        List.of(MaterialRequirementLineContribution.of(orderId, itemB, bd(20))));
        MaterialRequirement requirement =
                MaterialRequirement.create(
                        PROD,
                        T0,
                        List.of(
                                MaterialRequirementSourceItem.of(orderId, itemA, 1L),
                                MaterialRequirementSourceItem.of(orderId, itemB, 1L)),
                        List.of(lineA, lineB));

        MaterialRequirement edited =
                requirement.changeLineQuantities(
                        Map.of(lineA.lineId(), bd(3), lineB.lineId(), bd(7)), T1);

        assertEquals(0L, edited.version());
        assertEquals(0, edited.lines().get(0).quantity().compareTo(bd(3)));
        assertEquals(0, edited.lines().get(1).quantity().compareTo(bd(7)));
    }

    @Test
    void acceptsZeroBelowEqualAndAboveRequiredRejectsNegative() {
        MaterialRequirementLine line = sampleLine(bd(10));
        MaterialRequirement requirement =
                MaterialRequirement.create(PROD, T0, sampleSourceItems(line), List.of(line));

        MaterialRequirement zeroed =
                requirement.changeLineQuantity(line.lineId(), BigDecimal.ZERO, T1);
        assertEquals(0, zeroed.lines().getFirst().quantity().compareTo(BigDecimal.ZERO));
        assertEquals(0, zeroed.lines().getFirst().requiredQuantity().compareTo(bd(10)));

        MaterialRequirement above =
                zeroed.changeLineQuantity(line.lineId(), bd(11), Instant.parse("2026-09-09T06:00:00Z"));
        assertEquals(0, above.lines().getFirst().quantity().compareTo(bd(11)));
        assertEquals(0, above.lines().getFirst().requiredQuantity().compareTo(bd(10)));

        assertThrows(
                IllegalArgumentException.class,
                () -> requirement.changeLineQuantity(line.lineId(), bd(-1), T1));
    }

    @Test
    void requiredQuantityIsSumOfContributions() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemA = SourceOrderItemId.generate();
        SourceOrderItemId itemB = SourceOrderItemId.generate();
        MaterialRequirementLine line =
                MaterialRequirementLine.create(
                        MaterialReferenceId.generate(),
                        "MAT-1",
                        "Material",
                        "WHITE",
                        "PCS",
                        null,
                        bd(15),
                        List.of(
                                MaterialRequirementLineContribution.of(orderId, itemA, bd(10)),
                                MaterialRequirementLineContribution.of(orderId, itemB, bd(5))));

        assertEquals(0, line.requiredQuantity().compareTo(bd(15)));
        assertEquals(0, line.quantity().compareTo(bd(15)));

        MaterialRequirementLine reduced = line.changeQuantity(bd(12));
        assertEquals(0, reduced.requiredQuantity().compareTo(bd(15)));
        assertEquals(0, reduced.quantity().compareTo(bd(12)));
    }

    @Test
    void rejectsUnknownLineId() {
        MaterialRequirementLine line = sampleLine(bd(5));
        MaterialRequirement requirement =
                MaterialRequirement.create(PROD, T0, sampleSourceItems(line), List.of(line));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        requirement.changeLineQuantity(
                                MaterialRequirementLineId.generate(), bd(4), T1));
    }

    @Test
    void submitTransitionsDraftToSubmittedAndFreezesLines() {
        MaterialRequirementLine line = sampleLine(bd(100));
        MaterialRequirement requirement =
                MaterialRequirement.create(PROD, T0, sampleSourceItems(line), List.of(line));

        MaterialRequirement submitted = requirement.submit("user-1", T1);

        assertEquals(MaterialRequirementStatus.SUBMITTED, submitted.status());
        assertEquals(Optional.of(T1), submitted.submittedAt());
        assertEquals(Optional.of("user-1"), submitted.submittedBy());
        assertEquals(requirement.version(), submitted.version());
        assertEquals(requirement.destinationWarehouseId(), submitted.destinationWarehouseId());
        assertEquals(line.lineId(), submitted.lines().getFirst().lineId());
        assertEquals(0, submitted.lines().getFirst().quantity().compareTo(bd(100)));
        assertThrows(
                IllegalStateException.class,
                () -> submitted.changeLineQuantity(line.lineId(), bd(90), T1));
        assertThrows(IllegalStateException.class, () -> submitted.submit("user-2", T1));
    }

    @Test
    void submitAllowsAllZeroRequestQuantities() {
        MaterialRequirementLine line = sampleLine(bd(20));
        MaterialRequirement requirement =
                MaterialRequirement.create(PROD, T0, sampleSourceItems(line), List.of(line))
                        .changeLineQuantity(line.lineId(), BigDecimal.ZERO, T1);

        MaterialRequirement submitted = requirement.submit("user-1", T1);

        assertEquals(MaterialRequirementStatus.SUBMITTED, submitted.status());
        assertEquals(0, submitted.lines().getFirst().quantity().compareTo(BigDecimal.ZERO));
        assertEquals(0, submitted.lines().getFirst().requiredQuantity().compareTo(bd(20)));
    }

    private static List<MaterialRequirementSourceItem> sampleSourceItems(
            MaterialRequirementLine line) {
        MaterialRequirementLineContribution contribution = line.contributions().getFirst();
        return List.of(
                MaterialRequirementSourceItem.of(
                        contribution.sourceOrderId(),
                        contribution.sourceOrderItemId(),
                        1L));
    }

    private static MaterialRequirementLine sampleLine(BigDecimal quantity) {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        return MaterialRequirementLine.create(
                MaterialReferenceId.generate(),
                "MAT-1",
                "Material",
                "WHITE",
                "PCS",
                null,
                quantity,
                List.of(MaterialRequirementLineContribution.of(orderId, itemId, quantity)));
    }

    private static BigDecimal bd(long value) {
        return BigDecimal.valueOf(value);
    }
}
