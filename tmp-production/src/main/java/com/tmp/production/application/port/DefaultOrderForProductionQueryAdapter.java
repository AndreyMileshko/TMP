package com.tmp.production.application.port;

import com.tmp.order.api.OrderForProductionDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemForProductionDto;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.ProductionSpecificationDto;
import com.tmp.production.application.port.OrderForProductionQueryPort.ResolvedItemLine;
import com.tmp.production.application.port.OrderForProductionQueryPort.ResolvedOrderForLaunch;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Adapter over Order Management {@link OrderQueryService} public API. */
public final class DefaultOrderForProductionQueryAdapter implements OrderForProductionQueryPort {

    private final OrderQueryService orderQueryService;

    public DefaultOrderForProductionQueryAdapter(OrderQueryService orderQueryService) {
        this.orderQueryService = Objects.requireNonNull(orderQueryService, "orderQueryService");
    }

    @Override
    public Optional<ResolvedOrderForLaunch> resolveForLaunch(SourceOrderId sourceOrderId) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        return orderQueryService
                .getOrderForProduction(OrderId.of(sourceOrderId.value()))
                .map(this::toResolved);
    }

    private ResolvedOrderForLaunch toResolved(OrderForProductionDto dto) {
        List<ResolvedItemLine> lines = dto.items().stream().map(this::toResolvedLine).toList();
        return new ResolvedOrderForLaunch(
                SourceOrderId.of(dto.orderId().value()), dto.status(), dto.activeItemCount(), lines);
    }

    private ResolvedItemLine toResolvedLine(OrderItemForProductionDto item) {
        ProductionSpecificationDto specification = item.specification();
        return new ResolvedItemLine(
                SourceOrderItemId.of(item.orderItemId().value()),
                SpecificationId.of(specification.specificationId().value()),
                specification.orderedQuantity());
    }
}
