package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.RevisionNumber;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubOrderQuery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionOrderItemsLoaderTest {

    @Test
    void loadAllAggregatesEveryPageFromStub() {
        UUID orderId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        StubOrderQuery orderQuery = new StubOrderQuery();
        List<OrderItemDto> allItems = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            UUID itemId = UUID.nameUUIDFromBytes(("production-loader-item-" + i).getBytes());
            OrderItemDto dto =
                    OrderItemDto.of(
                            OrderItemId.of(itemId),
                            OrderId.of(orderId),
                            "P-" + i,
                            "Изделие " + i,
                            null,
                            Integer.toString(i + 1),
                            OrderItemStatus.ACTIVE,
                            RevisionNumber.first(),
                            Instant.parse("2026-01-01T00:00:00Z"),
                            Instant.parse("2026-01-01T00:00:00Z"));
            allItems.add(dto);
        }
        orderQuery.items.addAll(allItems);

        List<OrderItemDto> loaded =
                ProductionOrderItemsLoader.loadAll(orderQuery, OrderId.of(orderId));

        assertEquals(101, loaded.size());
        assertEquals(allItems, loaded);

        PageResult<OrderItemDto> page0 =
                orderQuery.getOrderItems(OrderId.of(orderId), PageRequest.of(0, PageRequest.MAX_PAGE_SIZE));
        PageResult<OrderItemDto> page1 =
                orderQuery.getOrderItems(OrderId.of(orderId), PageRequest.of(1, PageRequest.MAX_PAGE_SIZE));
        assertEquals(100, page0.content().size());
        assertEquals(1, page1.content().size());
    }
}
