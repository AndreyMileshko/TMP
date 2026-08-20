package com.tmp.production.application.port;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.ProductionSpecificationDto;
import com.tmp.order.api.SpecificationLineDto;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedSpecification;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Adapter over Order Management {@link OrderQueryService} public API.
 */
public final class DefaultOrderSpecificationQueryAdapter implements OrderSpecificationQueryPort {

    private final OrderQueryService orderQueryService;

    public DefaultOrderSpecificationQueryAdapter(OrderQueryService orderQueryService) {
        this.orderQueryService = Objects.requireNonNull(orderQueryService, "orderQueryService");
    }

    @Override
    public Optional<ResolvedSpecification> resolveCurrentForLaunch(
            SourceOrderItemId sourceOrderItemId) {
        Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        return orderQueryService
                .getCurrentItemSpecification(toOrderItemId(sourceOrderItemId))
                .map(this::toResolved);
    }

    @Override
    public Optional<ResolvedSpecification> resolveById(SpecificationId specificationId) {
        Objects.requireNonNull(specificationId, "specificationId");
        return orderQueryService
                .getSpecificationById(toOrderSpecificationId(specificationId))
                .map(this::toResolved);
    }

    private ResolvedSpecification toResolved(ProductionSpecificationDto dto) {
        List<ResolvedMaterialLine> lines =
                dto.lines().stream().map(this::toMaterialLine).toList();
        return new ResolvedSpecification(
                SpecificationId.of(dto.specificationId().value()),
                SourceOrderItemId.of(dto.orderItemId().value()),
                dto.orderedQuantity(),
                lines);
    }

    private ResolvedMaterialLine toMaterialLine(SpecificationLineDto line) {
        return new ResolvedMaterialLine(
                line.materialCode(),
                line.materialName(),
                line.color(),
                line.lengthMm(),
                line.lineQuantity(),
                line.unitOfMeasure());
    }

    private static OrderItemId toOrderItemId(SourceOrderItemId sourceOrderItemId) {
        return OrderItemId.of(sourceOrderItemId.value());
    }

    private static com.tmp.order.api.SpecificationId toOrderSpecificationId(
            SpecificationId specificationId) {
        return com.tmp.order.api.SpecificationId.of(specificationId.value());
    }
}
