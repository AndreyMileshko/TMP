package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import org.junit.jupiter.api.Test;

class ProductionPresentationLabelsTest {

    @Test
    void orderStatusUsesOrdersTableAwaitingCaptionForNotAccepted() {
        assertEquals(
                "Ожидает производства",
                ProductionPresentationLabels.orderStatus(OrderProductionViewStatus.NOT_ACCEPTED));
        assertEquals(
                "В производстве",
                ProductionPresentationLabels.orderStatus(OrderProductionViewStatus.IN_PRODUCTION));
        assertEquals(
                "Изготовлен",
                ProductionPresentationLabels.orderStatus(OrderProductionViewStatus.MANUFACTURED));
        assertEquals(
                "Отменён",
                ProductionPresentationLabels.orderStatus(OrderProductionViewStatus.CANCELLED));
    }

    @Test
    void itemStatusCoversCanonicalProductionStates() {
        assertEquals("Ожидает производства", ProductionPresentationLabels.itemStatus(null));
        assertEquals(
                "В производстве",
                ProductionPresentationLabels.itemStatus(ItemProductionStateStatus.IN_PRODUCTION));
        assertEquals(
                "Частично выпущено",
                ProductionPresentationLabels.itemStatus(
                        ItemProductionStateStatus.PARTIALLY_RELEASED));
        assertEquals(
                "Выпущено",
                ProductionPresentationLabels.itemStatus(ItemProductionStateStatus.RELEASED));
        assertEquals(
                "Отменено",
                ProductionPresentationLabels.itemStatus(ItemProductionStateStatus.CANCELLED));
    }
}
