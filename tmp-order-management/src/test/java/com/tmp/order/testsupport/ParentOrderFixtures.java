package com.tmp.order.testsupport;

import com.tmp.order.api.OrderId;
import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import java.time.Clock;

/** Test parent Customer Order seeds for item/revision use-case tests. */
public final class ParentOrderFixtures {

    private ParentOrderFixtures() {}

    public static CustomerOrder saveDraft(
            CustomerOrderRepository orders, OrderId orderId, Clock clock) {
        return orders.save(
                CustomerOrder.create(orderId, number(orderId), commercial(), clock));
    }

    public static CustomerOrder saveActive(
            CustomerOrderRepository orders, OrderId orderId, Clock clock) {
        return orders.save(
                CustomerOrder.create(orderId, number(orderId), commercial(), clock)
                        .approve(clock)
                        .activate(clock));
    }

    private static OrderNumber number(OrderId orderId) {
        return OrderNumber.of("ORD-" + orderId.value());
    }

    private static OrderCommercialData commercial() {
        return OrderCommercialData.of(
                "C-1",
                "Customer",
                "CTR",
                "SITE",
                "Mgr",
                OrderDirection.PRIVATE,
                CurrencyCode.of("USD"));
    }
}
