package com.tmp.production.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.application.ProductionHistoryService;
import com.tmp.production.domain.ProductionHistoryEntry.ProductionHistoryType;
import com.tmp.production.testsupport.InMemoryProductionHistoryRepository;
import com.tmp.production.testsupport.ProductionHistoryTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionHistoryServiceTest {

    private final InMemoryProductionHistoryRepository repository =
            new InMemoryProductionHistoryRepository();
    private final ProductionHistoryService history =
            ProductionHistoryTestSupport.historyService(repository);

    @Test
    void planFactDeviationSkippedWhenScaleDiffersButNumericEqual() {
        ProductionRelease release =
                releaseWithMaterials(
                        material(bd("10.0"), bd("10.000000")),
                        material(bd("4"), bd("4.00")));
        Optional<ProductionHistoryEntry> deviation =
                history.planFactDeviationIfAny(release, UUID.randomUUID());
        assertTrue(deviation.isEmpty());
    }

    @Test
    void planFactDeviationIncludesOnlyDeviatingLinesWithActualMinusPlanned() {
        UUID itemA = UUID.randomUUID();
        UUID itemB = UUID.randomUUID();
        UUID itemC = UUID.randomUUID();
        ProductionRelease release =
                releaseWithMaterials(
                        material(itemA, bd("10"), bd("8")),
                        material(itemB, bd("4"), bd("4")),
                        material(itemC, bd("5"), bd("7")));
        UUID documentId = UUID.randomUUID();
        ProductionHistoryEntry entry =
                history.planFactDeviationIfAny(release, documentId).orElseThrow();
        assertEquals(ProductionHistoryType.PLAN_FACT_DEVIATION, entry.historyType());
        assertEquals(documentId, entry.businessReferenceId().orElseThrow());
        String details = entry.detailsJson().orElseThrow();
        assertTrue(details.contains("\"actualMinusPlanned\":-2"));
        assertTrue(details.contains("\"actualMinusPlanned\":2"));
        assertTrue(!details.contains(itemB.toString()));
        assertTrue(details.contains(itemA.toString()));
        assertTrue(details.contains(itemC.toString()));
    }

    @Test
    void listByOrderFiltersAndOrdersChronologically() {
        SourceOrderId orderA = SourceOrderId.generate();
        SourceOrderId orderB = SourceOrderId.generate();
        Instant t1 = Instant.parse("2026-08-24T01:00:00Z");
        Instant t2 = Instant.parse("2026-08-24T02:00:00Z");
        Instant t3 = Instant.parse("2026-08-24T03:00:00Z");
        history.append(checkEntry(orderA, t2));
        history.append(checkEntry(orderB, t1));
        history.append(checkEntry(orderA, t1));
        history.append(checkEntry(orderA, t3));
        history.append(checkEntry(orderB, t3));

        List<ProductionHistoryEntry> forA = history.listByOrder(orderA);
        assertEquals(3, forA.size());
        assertEquals(t1, forA.get(0).occurredAt());
        assertEquals(t2, forA.get(1).occurredAt());
        assertEquals(t3, forA.get(2).occurredAt());
        assertTrue(forA.stream().allMatch(e -> e.sourceOrderId().equals(orderA)));
        assertTrue(history.listByOrder(SourceOrderId.generate()).isEmpty());
    }

    @Test
    void orderAcceptedCapturesActorAndItemCount() {
        SourceOrderId orderId = SourceOrderId.generate();
        UUID documentId = UUID.randomUUID();
        UUID item1 = UUID.randomUUID();
        UUID item2 = UUID.randomUUID();
        ProductionHistoryEntry entry =
                history.orderAccepted(
                        orderId,
                        documentId,
                        Instant.parse("2026-08-24T04:00:00Z"),
                        List.of(item1, item2),
                        Optional.of("operator"));
        assertEquals(ProductionHistoryType.ORDER_ACCEPTED, entry.historyType());
        assertEquals("operator", entry.actorRef().orElseThrow());
        assertEquals(documentId, entry.businessReferenceId().orElseThrow());
        assertTrue(entry.detailsJson().orElseThrow().contains("\"acceptedItemCount\":2"));
        assertTrue(entry.detailsJson().orElseThrow().contains(item1.toString()));
    }

    private ProductionHistoryEntry checkEntry(SourceOrderId orderId, Instant occurredAt) {
        return ProductionHistoryEntry.create(
                ProductionHistoryEntry.ProductionHistoryEntryId.generate(),
                orderId,
                ProductionHistoryType.MATERIALS_CHECKED,
                occurredAt,
                occurredAt.plusSeconds(1),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of("check"),
                Optional.of("{\"overallStatus\":\"ALL_AVAILABLE\"}"));
    }

    private static ProductionRelease releaseWithMaterials(ProductionRelease.MaterialLine... lines) {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        return ProductionRelease.draft(
                UUID.randomUUID(),
                orderId,
                Instant.parse("2026-08-24T12:00:00Z"),
                List.of(
                        new ProductionRelease.ItemLine(
                                itemId, SpecificationId.generate(), ProductionQuantity.positive(1))),
                List.of(lines));
    }

    private static ProductionRelease.MaterialLine material(BigDecimal planned, BigDecimal actual) {
        return material(UUID.randomUUID(), planned, actual);
    }

    private static ProductionRelease.MaterialLine material(
            UUID itemId, BigDecimal planned, BigDecimal actual) {
        return new ProductionRelease.MaterialLine(
                MaterialReferenceId.of(UUID.randomUUID()),
                planned,
                actual,
                MaterialPlanningSource.SPECIFICATION,
                Optional.empty(),
                Optional.of(SourceOrderItemId.of(itemId)),
                Optional.empty());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
