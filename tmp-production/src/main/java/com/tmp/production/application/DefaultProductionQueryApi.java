package com.tmp.production.application;

import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.domain.MaterialAvailabilityCheckResult;
import com.tmp.production.domain.MaterialAvailabilityLine;
import com.tmp.production.domain.MaterialCheckNotAllowedException;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.ProductionHistoryEntry;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.security.ProductionPermissions;
import com.tmp.security.api.AuthorizationService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default Production Public Query API implementation (Production Spec §18.1).
 *
 * <p>All methods are read-only and require {@code production.order.view} permission before
 * any downstream Production/Warehouse reads.
 */
public final class DefaultProductionQueryApi implements ProductionQueryApi {

    private final AuthorizationService authorizationService;
    private final ProductionOrderViewService orderViewService;
    private final CurrentMaterialAvailabilityQueryService materialAvailabilityQueryService;
    private final ProductionHistoryService historyService;

    public DefaultProductionQueryApi(
            AuthorizationService authorizationService,
            ProductionOrderViewService orderViewService,
            CurrentMaterialAvailabilityQueryService materialAvailabilityQueryService,
            ProductionHistoryService historyService) {
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService");
        this.orderViewService = Objects.requireNonNull(orderViewService, "orderViewService");
        this.materialAvailabilityQueryService = Objects.requireNonNull(materialAvailabilityQueryService, "materialAvailabilityQueryService");
        this.historyService = Objects.requireNonNull(historyService, "historyService");
    }

    @Override
    public ProductionQueryApi.OrderProductionView getOrderProductionView(UUID orderId) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        Objects.requireNonNull(orderId, "orderId");
        SourceOrderId sourceOrderId = SourceOrderId.of(orderId);

        com.tmp.production.domain.OrderProductionView view =
                orderViewService.getOrderProductionView(sourceOrderId);
        return map(view);
    }

    @Override
    public Map<UUID, ProductionQueryApi.OrderProductionListFacts> getOrderProductionListFacts(
            Collection<UUID> orderIds) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        Objects.requireNonNull(orderIds, "orderIds");
        Map<UUID, ProductionOrderViewService.ProductionListFacts> facts =
                orderViewService.listProductionListFacts(orderIds);
        Map<UUID, ProductionQueryApi.OrderProductionListFacts> mapped = new LinkedHashMap<>();
        for (Map.Entry<UUID, ProductionOrderViewService.ProductionListFacts> entry : facts.entrySet()) {
            ProductionOrderViewService.ProductionListFacts value = entry.getValue();
            mapped.put(
                    entry.getKey(),
                    new ProductionQueryApi.OrderProductionListFacts(
                            value.sourceOrderId(),
                            map(value.status()),
                            value.orderedQuantity(),
                            value.releasedQuantity(),
                            value.activeProductionQuantity(),
                            value.cancellationPosted()));
        }
        return Map.copyOf(mapped);
    }

    @Override
    public Optional<ProductionQueryApi.ItemProductionStateView> getItemProductionState(UUID orderItemId) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        Objects.requireNonNull(orderItemId, "orderItemId");
        SourceOrderItemId itemId = SourceOrderItemId.of(orderItemId);

        return orderViewService
                .findItemProductionStateByOrderItemId(itemId)
                .map(this::map);
    }

    @Override
    public Optional<ProductionQueryApi.MaterialAvailabilityResultView> getMaterialAvailabilityResult(UUID orderId) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        Objects.requireNonNull(orderId, "orderId");
        SourceOrderId sourceOrderId = SourceOrderId.of(orderId);

        try {
            MaterialAvailabilityCheckResult result =
                    materialAvailabilityQueryService.evaluate(sourceOrderId);
            return Optional.of(map(result));
        } catch (MaterialCheckNotAllowedException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<ProductionQueryApi.ProductionHistoryEntryView> listProductionHistory(UUID orderId) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        Objects.requireNonNull(orderId, "orderId");
        SourceOrderId sourceOrderId = SourceOrderId.of(orderId);

        return List.copyOf(
                historyService.listByOrder(sourceOrderId).stream().map(this::map).toList());
    }

    private ProductionQueryApi.OrderProductionView map(
            com.tmp.production.domain.OrderProductionView view) {
        return new ProductionQueryApi.OrderProductionView(
                view.sourceOrderId().value(),
                map(view.status()),
                view.itemCount(),
                view.inProductionCount(),
                view.partiallyReleasedCount(),
                view.releasedCount(),
                view.cancelledCount());
    }

    private ProductionQueryApi.OrderProductionViewStatus map(
            com.tmp.production.domain.OrderProductionViewStatus status) {
        return switch (status) {
            case NOT_ACCEPTED -> ProductionQueryApi.OrderProductionViewStatus.NOT_ACCEPTED;
            case IN_PRODUCTION -> ProductionQueryApi.OrderProductionViewStatus.IN_PRODUCTION;
            case MANUFACTURED -> ProductionQueryApi.OrderProductionViewStatus.MANUFACTURED;
            case CANCELLED -> ProductionQueryApi.OrderProductionViewStatus.CANCELLED;
        };
    }

    private ProductionQueryApi.ItemProductionStateView map(ProductionItemState state) {
        return new ProductionQueryApi.ItemProductionStateView(
                state.sourceOrderId().value(),
                state.sourceOrderItemId().value(),
                state.specificationId().value(),
                map(state.status()),
                state.orderedQuantity().value().longValueExact(),
                state.launchedQuantity().value().longValueExact(),
                state.activeProductionQuantity().value().longValueExact(),
                state.releasedQuantity().value().longValueExact(),
                Optional.ofNullable(state.lastMaterialCheckAt()),
                state.lastStatusChangedAt(),
                state.cuttingPlanLinks().asList().stream()
                        .map(this::map)
                        .toList());
    }

    private ProductionQueryApi.ItemProductionStateStatus map(ProductionStatus status) {
        return switch (status) {
            case IN_PRODUCTION -> ProductionQueryApi.ItemProductionStateStatus.IN_PRODUCTION;
            case PARTIALLY_RELEASED -> ProductionQueryApi.ItemProductionStateStatus.PARTIALLY_RELEASED;
            case RELEASED -> ProductionQueryApi.ItemProductionStateStatus.RELEASED;
            case CANCELLED -> ProductionQueryApi.ItemProductionStateStatus.CANCELLED;
            case NOT_STARTED ->
                    throw new IllegalStateException("Public API must not expose NOT_STARTED");
        };
    }

    private ProductionQueryApi.CuttingPlanLinkView map(com.tmp.production.domain.ProductionCuttingPlanLink link) {
        return new ProductionQueryApi.CuttingPlanLinkView(
                link.materialReferenceId().value(), link.cuttingPlanId().value());
    }

    private ProductionQueryApi.MaterialAvailabilityResultView map(MaterialAvailabilityCheckResult result) {
        return new ProductionQueryApi.MaterialAvailabilityResultView(
                result.sourceOrderId().value(),
                result.checkedAt(),
                map(result.overallStatus()),
                result.lines().stream().map(this::map).toList());
    }

    private ProductionQueryApi.MaterialAvailabilityOverallStatus map(
            com.tmp.production.domain.MaterialAvailabilityOverallStatus status) {
        return switch (status) {
            case ALL_AVAILABLE -> ProductionQueryApi.MaterialAvailabilityOverallStatus.ALL_AVAILABLE;
            case HAS_DEFICIT -> ProductionQueryApi.MaterialAvailabilityOverallStatus.HAS_DEFICIT;
            case HAS_UNRESOLVED_MATERIALS ->
                    ProductionQueryApi.MaterialAvailabilityOverallStatus.HAS_UNRESOLVED_MATERIALS;
        };
    }

    private ProductionQueryApi.MaterialAvailabilityLineView map(MaterialAvailabilityLine line) {
        return new ProductionQueryApi.MaterialAvailabilityLineView(
                line.materialCode(),
                line.materialName(),
                line.color(),
                line.unitOfMeasure(),
                Optional.ofNullable(line.materialReferenceId()),
                line.requiredQuantity(),
                line.mainWarehouseAvailable(),
                line.productionWarehouseAvailable(),
                line.totalAvailable(),
                line.deficit(),
                map(line.status()),
                map(line.planningSource()));
    }

    private ProductionQueryApi.MaterialAvailabilityLineStatus map(
            com.tmp.production.domain.MaterialAvailabilityLineStatus status) {
        return switch (status) {
            case AVAILABLE -> ProductionQueryApi.MaterialAvailabilityLineStatus.AVAILABLE;
            case INSUFFICIENT -> ProductionQueryApi.MaterialAvailabilityLineStatus.INSUFFICIENT;
            case MATERIAL_UNRESOLVED ->
                    ProductionQueryApi.MaterialAvailabilityLineStatus.MATERIAL_UNRESOLVED;
            case MATERIAL_AMBIGUOUS ->
                    ProductionQueryApi.MaterialAvailabilityLineStatus.MATERIAL_AMBIGUOUS;
        };
    }

    private ProductionQueryApi.MaterialPlanningSourceView map(MaterialPlanningSource source) {
        return switch (source) {
            case SPECIFICATION -> ProductionQueryApi.MaterialPlanningSourceView.SPECIFICATION;
            case CUTTING_PLAN -> ProductionQueryApi.MaterialPlanningSourceView.CUTTING_PLAN;
        };
    }

    private ProductionQueryApi.ProductionHistoryEntryView map(ProductionHistoryEntry entry) {
        return new ProductionQueryApi.ProductionHistoryEntryView(
                entry.entryId().value(),
                entry.sourceOrderId().value(),
                map(entry.historyType()),
                entry.occurredAt(),
                entry.recordedAt(),
                entry.sourceOrderItemId().map(SourceOrderItemId::value),
                entry.sourceDocumentId(),
                entry.businessReferenceId(),
                entry.actorRef(),
                entry.summary());
    }

    private ProductionQueryApi.ProductionHistoryType map(ProductionHistoryEntry.ProductionHistoryType type) {
        return ProductionQueryApi.ProductionHistoryType.valueOf(type.name());
    }
}

