package com.tmp.production.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ProductionItemStateTest {

    private static final Instant T0 = Instant.parse("2026-08-19T06:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-19T07:00:00Z");
    private static final Instant T2 = Instant.parse("2026-08-19T08:00:00Z");

    @Test
    void launchCreatesInProductionStateWithNonNullSpecificationReference() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specificationId = SpecificationId.generate();

        ProductionItemState state =
                ProductionItemState.launch(
                        orderId, itemId, specificationId, ProductionQuantity.positive(10), T0);

        assertEquals(orderId, state.sourceOrderId());
        assertEquals(itemId, state.sourceOrderItemId());
        assertEquals(specificationId, state.specificationId());
        assertEquals(ProductionStatus.IN_PRODUCTION, state.status());
        assertEquals(ProductionQuantity.positive(10), state.orderedQuantity());
        assertEquals(ProductionQuantity.positive(10), state.launchedQuantity());
        assertEquals(ProductionQuantity.positive(10), state.activeProductionQuantity());
        assertEquals(ProductionQuantity.zero(), state.releasedQuantity());
        assertNull(state.lastMaterialCheckAt());
        assertEquals(T0, state.lastStatusChangedAt());
    }

    @Test
    void launchRejectsNullIdentifiers() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specificationId = SpecificationId.generate();
        ProductionQuantity ordered = ProductionQuantity.positive(1);

        assertThrows(
                NullPointerException.class,
                () -> ProductionItemState.launch(null, itemId, specificationId, ordered, T0));
        assertThrows(
                NullPointerException.class,
                () -> ProductionItemState.launch(orderId, null, specificationId, ordered, T0));
        assertThrows(
                NullPointerException.class,
                () -> ProductionItemState.launch(orderId, itemId, null, ordered, T0));
        assertThrows(
                NullPointerException.class,
                () -> ProductionItemState.launch(orderId, itemId, specificationId, null, T0));
    }

    @Test
    void partialReleaseMovesToPartiallyReleased() {
        ProductionItemState launched = sampleLaunched(10);
        ProductionItemState partial = launched.release(ProductionQuantity.positive(4), T1);

        assertEquals(ProductionStatus.PARTIALLY_RELEASED, partial.status());
        assertEquals(ProductionQuantity.nonNegative(6), partial.activeProductionQuantity());
        assertEquals(ProductionQuantity.nonNegative(4), partial.releasedQuantity());
        assertEquals(T1, partial.lastStatusChangedAt());
    }

    @Test
    void fullReleaseMovesToReleased() {
        ProductionItemState launched = sampleLaunched(10);
        ProductionItemState released = launched.release(ProductionQuantity.positive(10), T1);

        assertEquals(ProductionStatus.RELEASED, released.status());
        assertEquals(ProductionQuantity.zero(), released.activeProductionQuantity());
        assertEquals(ProductionQuantity.positive(10), released.releasedQuantity());
    }

    @Test
    void incrementalReleaseCanReachReleased() {
        ProductionItemState state = sampleLaunched(10);
        state = state.release(ProductionQuantity.positive(4), T0);
        state = state.release(ProductionQuantity.positive(6), T1);

        assertEquals(ProductionStatus.RELEASED, state.status());
        assertEquals(ProductionQuantity.positive(10), state.releasedQuantity());
    }

    @Test
    void negativeReleaseAmountIsRejected() {
        ProductionItemState launched = sampleLaunched(10);
        assertThrows(
                IllegalArgumentException.class,
                () -> launched.release(ProductionQuantity.positive(0), T1));
    }

    @Test
    void overReleaseIsRejected() {
        ProductionItemState launched = sampleLaunched(10);
        assertThrows(
                InvalidProductionStateException.class,
                () -> launched.release(ProductionQuantity.positive(11), T1));
    }

    @Test
    void releasedQuantityCannotExceedLaunchedQuantityThroughPartialSteps() {
        ProductionItemState afterFirstRelease =
                sampleLaunched(5).release(ProductionQuantity.positive(3), T0);
        assertThrows(
                InvalidProductionStateException.class,
                () -> afterFirstRelease.release(ProductionQuantity.positive(3), T1));
    }

    @Test
    void cancelUnfinishedProduction() {
        ProductionItemState launched = sampleLaunched(10);
        ProductionItemState cancelled = launched.cancel(T1);

        assertEquals(ProductionStatus.CANCELLED, cancelled.status());
        assertEquals(ProductionQuantity.zero(), cancelled.activeProductionQuantity());
        assertEquals(ProductionQuantity.zero(), cancelled.releasedQuantity());
        assertEquals(T1, cancelled.lastStatusChangedAt());
    }

    @Test
    void cancelPartiallyReleasedPreservesReleasedQuantity() {
        ProductionItemState partial =
                sampleLaunched(10).release(ProductionQuantity.positive(4), T0);
        ProductionItemState cancelled = partial.cancel(T1);

        assertEquals(ProductionStatus.CANCELLED, cancelled.status());
        assertEquals(ProductionQuantity.nonNegative(4), cancelled.releasedQuantity());
        assertEquals(ProductionQuantity.zero(), cancelled.activeProductionQuantity());
    }

    @Test
    void releasedProductionCannotBeCancelled() {
        ProductionItemState released =
                sampleLaunched(5).release(ProductionQuantity.positive(5), T1);
        assertThrows(InvalidProductionStateException.class, () -> released.cancel(T2));
    }

    @Test
    void cancelledProductionCannotBeReleased() {
        ProductionItemState cancelled = sampleLaunched(5).cancel(T1);
        assertThrows(
                InvalidProductionStateException.class,
                () -> cancelled.release(ProductionQuantity.positive(1), T2));
    }

    @Test
    void cancelledProductionCannotBeCancelledAgain() {
        ProductionItemState cancelled = sampleLaunched(5).cancel(T1);
        assertThrows(InvalidProductionStateException.class, () -> cancelled.cancel(T2));
    }

    @Test
    void recordMaterialCheckUpdatesTimestampOnly() {
        ProductionItemState launched = sampleLaunched(10);
        ProductionItemState checked = launched.recordMaterialCheck(T1);

        assertEquals(T1, checked.lastMaterialCheckAt());
        assertEquals(ProductionStatus.IN_PRODUCTION, checked.status());
        assertEquals(launched.activeProductionQuantity(), checked.activeProductionQuantity());
        assertEquals(launched.lastStatusChangedAt(), checked.lastStatusChangedAt());
    }

    @Test
    void materialCheckForbiddenForCancelledProduction() {
        ProductionItemState cancelled = sampleLaunched(5).cancel(T1);
        assertThrows(
                InvalidProductionStateException.class, () -> cancelled.recordMaterialCheck(T2));
    }

    @Test
    void twoOrderItemsHaveIndependentStates() {
        ProductionItemState first =
                ProductionItemState.launch(
                        SourceOrderId.generate(),
                        SourceOrderItemId.generate(),
                        SpecificationId.generate(),
                        ProductionQuantity.positive(3),
                        T0);
        ProductionItemState second =
                ProductionItemState.launch(
                        SourceOrderId.generate(),
                        SourceOrderItemId.generate(),
                        SpecificationId.generate(),
                        ProductionQuantity.positive(7),
                        T0);

        assertNotEquals(first.sourceOrderItemId(), second.sourceOrderItemId());
        assertNotEquals(first.specificationId(), second.specificationId());
        assertNotEquals(first.orderedQuantity(), second.orderedQuantity());
    }

    @Test
    void productionDomainHasNoRevisionFields() {
        Set<String> revisionLikeFields =
                Arrays.stream(ProductionItemState.class.getDeclaredFields())
                        .map(Field::getName)
                        .filter(name -> name.toLowerCase().contains("revision"))
                        .collect(Collectors.toSet());
        assertTrue(revisionLikeFields.isEmpty(), "Unexpected revision fields: " + revisionLikeFields);
    }

    @Test
    void statusVocabularyIncludesNotStartedWithoutPersistedStateFactory() {
        assertEquals(ProductionStatus.NOT_STARTED, ProductionStatus.valueOf("NOT_STARTED"));
    }

    private static ProductionItemState sampleLaunched(long ordered) {
        return ProductionItemState.launch(
                SourceOrderId.generate(),
                SourceOrderItemId.generate(),
                SpecificationId.generate(),
                ProductionQuantity.positive(ordered),
                T0);
    }
}
