package com.tmp.production.application.port;

import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderQueryService;
import com.tmp.production.domain.SourceOrderId;
import java.util.Objects;
import java.util.Optional;

/** Adapter over Order Management {@link OrderQueryService#getOrder} for customer order numbers. */
public final class DefaultCustomerOrderNumberQueryAdapter implements CustomerOrderNumberQueryPort {

    private final OrderQueryService orderQueryService;

    public DefaultCustomerOrderNumberQueryAdapter(OrderQueryService orderQueryService) {
        this.orderQueryService = Objects.requireNonNull(orderQueryService, "orderQueryService");
    }

    @Override
    public Optional<String> findOrderNumber(SourceOrderId sourceOrderId) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        return orderQueryService
                .getOrder(OrderId.of(sourceOrderId.value()))
                .map(OrderDto::orderNumber)
                .map(String::trim)
                .filter(number -> !number.isEmpty());
    }
}
