package com.tmp.production.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production Public Query API (Production Spec §18.1).
 *
 * <p>Read-only inter-capability contract: it returns only public DTOs and never exposes internal
 * domain/persistence types.
 */
public interface ProductionQueryApi {

    OrderProductionView getOrderProductionView(UUID orderId);

    /**
     * Batch read of Production facts for the operational Orders list.
     *
     * <p>The default implementation exists so test doubles compile; it is N+1 and MUST be overridden
     * in the production adapter.
     */
    default Map<UUID, OrderProductionListFacts> getOrderProductionListFacts(Collection<UUID> orderIds) {
        Objects.requireNonNull(orderIds, "orderIds");
        Map<UUID, OrderProductionListFacts> facts = new LinkedHashMap<>();
        for (UUID orderId : orderIds) {
            Objects.requireNonNull(orderId, "orderId");
            OrderProductionView view = getOrderProductionView(orderId);
            facts.put(
                    orderId,
                    new OrderProductionListFacts(
                            orderId,
                            view.status(),
                            0L,
                            0L,
                            0L,
                            view.status() == OrderProductionViewStatus.CANCELLED));
        }
        return facts;
    }

    Optional<ItemProductionStateView> getItemProductionState(UUID orderItemId);

    Optional<MaterialAvailabilityResultView> getMaterialAvailabilityResult(UUID orderId);

    List<ProductionHistoryEntryView> listProductionHistory(UUID orderId);

    enum OrderProductionViewStatus {
        NOT_ACCEPTED,
        IN_PRODUCTION,
        MANUFACTURED,
        CANCELLED
    }

    /**
     * Compact Production facts for one order on the operational list. Quantities are sums across
     * item-owned Production states; zeros when the order has not been accepted into Production.
     */
    record OrderProductionListFacts(
            UUID sourceOrderId,
            OrderProductionViewStatus status,
            long orderedQuantity,
            long releasedQuantity,
            long activeProductionQuantity,
            boolean cancellationPosted) {
        public OrderProductionListFacts {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(status, "status");
            if (orderedQuantity < 0L
                    || releasedQuantity < 0L
                    || activeProductionQuantity < 0L) {
                throw new IllegalArgumentException("Quantities must be >= 0");
            }
        }
    }

    record OrderProductionView(
            UUID sourceOrderId,
            OrderProductionViewStatus status,
            int itemCount,
            int inProductionCount,
            int partiallyReleasedCount,
            int releasedCount,
            int cancelledCount) {
        public OrderProductionView {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(status, "status");
            if (itemCount < 0
                    || inProductionCount < 0
                    || partiallyReleasedCount < 0
                    || releasedCount < 0
                    || cancelledCount < 0) {
                throw new IllegalArgumentException("Counts must be >= 0");
            }
        }
    }

    enum ItemProductionStateStatus {
        IN_PRODUCTION,
        PARTIALLY_RELEASED,
        RELEASED,
        CANCELLED
    }

    record CuttingPlanLinkView(UUID materialReferenceId, UUID cuttingPlanId) {
        public CuttingPlanLinkView {
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(cuttingPlanId, "cuttingPlanId");
        }
    }

    record ItemProductionStateView(
            UUID sourceOrderId,
            UUID sourceOrderItemId,
            UUID specificationId,
            ItemProductionStateStatus status,
            long orderedQuantity,
            long launchedQuantity,
            long activeProductionQuantity,
            long releasedQuantity,
            Optional<Instant> lastMaterialCheckAt,
            Instant lastStatusChangedAt,
            List<CuttingPlanLinkView> cuttingPlanLinks) {
        public ItemProductionStateView {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(specificationId, "specificationId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(lastMaterialCheckAt, "lastMaterialCheckAt");
            Objects.requireNonNull(lastStatusChangedAt, "lastStatusChangedAt");
            Objects.requireNonNull(cuttingPlanLinks, "cuttingPlanLinks");
            cuttingPlanLinks = List.copyOf(cuttingPlanLinks);
        }
    }

    enum MaterialAvailabilityLineStatus {
        AVAILABLE,
        INSUFFICIENT,
        MATERIAL_UNRESOLVED,
        MATERIAL_AMBIGUOUS
    }

    enum MaterialAvailabilityOverallStatus {
        ALL_AVAILABLE,
        HAS_DEFICIT,
        HAS_UNRESOLVED_MATERIALS
    }

    enum MaterialPlanningSourceView {
        SPECIFICATION,
        CUTTING_PLAN
    }

    record MaterialAvailabilityLineView(
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            Optional<UUID> materialReferenceId,
            BigDecimal requiredQuantity,
            BigDecimal mainWarehouseAvailable,
            BigDecimal productionWarehouseAvailable,
            BigDecimal totalAvailable,
            BigDecimal deficit,
            MaterialAvailabilityLineStatus status,
            MaterialPlanningSourceView planningSource) {
        public MaterialAvailabilityLineView {
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(materialName, "materialName");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(requiredQuantity, "requiredQuantity");
            Objects.requireNonNull(mainWarehouseAvailable, "mainWarehouseAvailable");
            Objects.requireNonNull(productionWarehouseAvailable, "productionWarehouseAvailable");
            Objects.requireNonNull(totalAvailable, "totalAvailable");
            Objects.requireNonNull(deficit, "deficit");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(planningSource, "planningSource");
        }
    }

    record MaterialAvailabilityResultView(
            UUID sourceOrderId,
            Instant evaluatedAt,
            MaterialAvailabilityOverallStatus overallStatus,
            List<MaterialAvailabilityLineView> lines) {
        public MaterialAvailabilityResultView {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(evaluatedAt, "evaluatedAt");
            Objects.requireNonNull(overallStatus, "overallStatus");
            Objects.requireNonNull(lines, "lines");
            lines = List.copyOf(lines);
        }
    }

    enum ProductionHistoryType {
        ORDER_ACCEPTED,
        MATERIALS_CHECKED,
        MATERIAL_TRANSFER_CREATED,
        MATERIAL_RECEIPT_CONFIRMED,
        PRODUCTS_RELEASED,
        PLAN_FACT_DEVIATION,
        PRODUCTION_CANCELLED
    }

    record ProductionHistoryEntryView(
            UUID entryId,
            UUID sourceOrderId,
            ProductionHistoryType historyType,
            Instant occurredAt,
            Instant recordedAt,
            Optional<UUID> sourceOrderItemId,
            Optional<UUID> sourceDocumentId,
            Optional<UUID> businessReferenceId,
            Optional<String> actorRef,
            Optional<String> summary) {
        public ProductionHistoryEntryView {
            Objects.requireNonNull(entryId, "entryId");
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(historyType, "historyType");
            Objects.requireNonNull(occurredAt, "occurredAt");
            Objects.requireNonNull(recordedAt, "recordedAt");
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(sourceDocumentId, "sourceDocumentId");
            Objects.requireNonNull(businessReferenceId, "businessReferenceId");
            Objects.requireNonNull(actorRef, "actorRef");
            Objects.requireNonNull(summary, "summary");
        }
    }
}

