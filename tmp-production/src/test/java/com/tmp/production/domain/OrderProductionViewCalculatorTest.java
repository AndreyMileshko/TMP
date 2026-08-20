package com.tmp.production.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tmp.production.domain.OrderProductionViewCalculator.Context;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderProductionViewCalculatorTest {

    private static final Instant T0 = Instant.parse("2026-08-20T08:00:00Z");
    private final OrderProductionViewCalculator calculator = new OrderProductionViewCalculator();

    @Test
    void emptyStatesAreNotAcceptedNotManufactured() {
        SourceOrderId orderId = SourceOrderId.generate();
        OrderProductionView view = calculator.calculate(orderId, List.of(), Context.none());
        assertEquals(OrderProductionViewStatus.NOT_ACCEPTED, view.status());
        assertEquals(0, view.itemCount());
    }

    @Test
    void case1SingleInProduction() {
        assertStatus(List.of(ProductionStatus.IN_PRODUCTION), OrderProductionViewStatus.IN_PRODUCTION);
    }

    @Test
    void case2TwoInProduction() {
        assertStatus(
                List.of(ProductionStatus.IN_PRODUCTION, ProductionStatus.IN_PRODUCTION),
                OrderProductionViewStatus.IN_PRODUCTION);
    }

    @Test
    void case3InProductionAndPartiallyReleased() {
        assertStatus(
                List.of(ProductionStatus.IN_PRODUCTION, ProductionStatus.PARTIALLY_RELEASED),
                OrderProductionViewStatus.IN_PRODUCTION);
    }

    @Test
    void case4TwoPartiallyReleased() {
        assertStatus(
                List.of(ProductionStatus.PARTIALLY_RELEASED, ProductionStatus.PARTIALLY_RELEASED),
                OrderProductionViewStatus.IN_PRODUCTION);
    }

    @Test
    void case5ReleasedAndInProduction() {
        assertStatus(
                List.of(ProductionStatus.RELEASED, ProductionStatus.IN_PRODUCTION),
                OrderProductionViewStatus.IN_PRODUCTION);
    }

    @Test
    void case6ReleasedAndPartiallyReleased() {
        assertStatus(
                List.of(ProductionStatus.RELEASED, ProductionStatus.PARTIALLY_RELEASED),
                OrderProductionViewStatus.IN_PRODUCTION);
    }

    @Test
    void case7AllReleasedIsManufactured() {
        assertStatus(
                List.of(ProductionStatus.RELEASED, ProductionStatus.RELEASED),
                OrderProductionViewStatus.MANUFACTURED);
    }

    @Test
    void case8AllCancelled() {
        assertStatus(
                List.of(ProductionStatus.CANCELLED, ProductionStatus.CANCELLED),
                OrderProductionViewStatus.CANCELLED);
    }

    @Test
    void case9ReleasedAndCancelledWithWholeOrderCancellation() {
        SourceOrderId orderId = SourceOrderId.generate();
        OrderProductionView view =
                calculator.calculate(
                        orderId,
                        states(orderId, ProductionStatus.RELEASED, ProductionStatus.CANCELLED),
                        Context.cancelled());
        assertEquals(OrderProductionViewStatus.CANCELLED, view.status());
    }

    @Test
    void case10ReleasedReleasedCancelledWithCancellation() {
        SourceOrderId orderId = SourceOrderId.generate();
        OrderProductionView view =
                calculator.calculate(
                        orderId,
                        states(
                                orderId,
                                ProductionStatus.RELEASED,
                                ProductionStatus.RELEASED,
                                ProductionStatus.CANCELLED),
                        Context.cancelled());
        assertEquals(OrderProductionViewStatus.CANCELLED, view.status());
    }

    @Test
    void cancelledPlusInProductionWithoutCancellationIsInProduction() {
        assertStatus(
                List.of(ProductionStatus.CANCELLED, ProductionStatus.IN_PRODUCTION),
                OrderProductionViewStatus.IN_PRODUCTION);
    }

    @Test
    void releasedPlusCancelledWithoutCancellationContextIsRejected() {
        SourceOrderId orderId = SourceOrderId.generate();
        assertThrows(
                InvalidProductionStateException.class,
                () ->
                        calculator.calculate(
                                orderId,
                                states(orderId, ProductionStatus.RELEASED, ProductionStatus.CANCELLED),
                                Context.none()));
    }

    @Test
    void duplicateItemStatesAreRejected() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        ProductionItemState first =
                state(orderId, itemId, SpecificationId.generate(), ProductionStatus.IN_PRODUCTION);
        ProductionItemState second =
                state(orderId, itemId, SpecificationId.generate(), ProductionStatus.IN_PRODUCTION);
        assertThrows(
                InvalidProductionStateException.class,
                () -> calculator.calculate(orderId, List.of(first, second), Context.none()));
    }

    private void assertStatus(List<ProductionStatus> statuses, OrderProductionViewStatus expected) {
        SourceOrderId orderId = SourceOrderId.generate();
        OrderProductionView view =
                calculator.calculate(orderId, states(orderId, statuses.toArray(ProductionStatus[]::new)), Context.none());
        assertEquals(expected, view.status());
        assertEquals(statuses.size(), view.itemCount());
    }

    private static List<ProductionItemState> states(
            SourceOrderId orderId, ProductionStatus... statuses) {
        return java.util.Arrays.stream(statuses)
                .map(
                        status ->
                                state(
                                        orderId,
                                        SourceOrderItemId.generate(),
                                        SpecificationId.generate(),
                                        status))
                .toList();
    }

    private static ProductionItemState state(
            SourceOrderId orderId,
            SourceOrderItemId itemId,
            SpecificationId specificationId,
            ProductionStatus status) {
        ProductionFoundation foundation =
                ProductionFoundation.freeze(orderId, itemId, specificationId, T0);
        ProductionItemState launched =
                ProductionItemState.launch(foundation, ProductionQuantity.positive(10), T0);
        return switch (status) {
            case IN_PRODUCTION -> launched;
            case PARTIALLY_RELEASED -> launched.release(ProductionQuantity.positive(4), T0);
            case RELEASED -> launched.release(ProductionQuantity.positive(10), T0);
            case CANCELLED -> launched.cancel(T0);
            case NOT_STARTED -> throw new IllegalArgumentException("NOT_STARTED not persisted");
        };
    }
}
