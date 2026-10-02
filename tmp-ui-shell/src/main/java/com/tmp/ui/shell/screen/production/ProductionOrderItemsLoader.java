package com.tmp.ui.shell.screen.production;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads every Order Item for an order via the public paginated query — never silently truncates to
 * the first page.
 */
public final class ProductionOrderItemsLoader {

    private ProductionOrderItemsLoader() {}

    public static List<OrderItemDto> loadAll(OrderQueryService orderQueryService, OrderId orderId) {
        Objects.requireNonNull(orderQueryService, "orderQueryService");
        Objects.requireNonNull(orderId, "orderId");
        List<OrderItemDto> all = new ArrayList<>();
        int pageIndex = 0;
        long total;
        do {
            PageRequest request = PageRequest.of(pageIndex, PageRequest.MAX_PAGE_SIZE);
            PageResult<OrderItemDto> page = orderQueryService.getOrderItems(orderId, request);
            all.addAll(page.content());
            total = page.totalElements();
            pageIndex++;
            if (page.content().isEmpty()) {
                break;
            }
        } while (all.size() < total);
        return List.copyOf(all);
    }
}
