package com.tmp.production.application;

import com.tmp.production.application.MaterialReadinessResult.MaterialReadinessLine;
import com.tmp.production.application.MaterialReadinessResult.MaterialReadinessReason;
import com.tmp.production.application.MaterialReadinessResult.MaterialReadinessStatus;
import com.tmp.production.application.ReleaseMaterialPlanBuilder.PlannedMaterialLine;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.MaterialReferenceEntry;
import com.tmp.production.domain.FrozenSpecificationUnavailableException;
import com.tmp.production.domain.OrderProductionView;
import com.tmp.production.domain.OrderProductionViewStatus;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.ReleaseProductsException;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Side-effect-free Production material readiness read model (Stage 7 Phase 6).
 *
 * <p>Required quantities come from the same {@link ReleaseMaterialPlanBuilder} used by Release
 * preview/confirm. Available quantities are current Warehouse {@code AVAILABLE} on the production
 * warehouse only. Does not write Production history, MR, reservations, or Warehouse operations.
 */
public final class MaterialReadinessQueryService {

    private final ProductionOrderViewService orderViewService;
    private final ProductionFoundationQueryService foundationQuery;
    private final WarehouseAvailabilityQueryPort warehouseQuery;
    private final ProductionDestinationWarehouse destinationWarehouse;
    private final ReleaseMaterialPlanBuilder planBuilder;

    public MaterialReadinessQueryService(
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQuery,
            WarehouseAvailabilityQueryPort warehouseQuery,
            ProductionDestinationWarehouse destinationWarehouse) {
        this(
                orderViewService,
                foundationQuery,
                warehouseQuery,
                destinationWarehouse,
                new ReleaseMaterialPlanBuilder());
    }

    MaterialReadinessQueryService(
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQuery,
            WarehouseAvailabilityQueryPort warehouseQuery,
            ProductionDestinationWarehouse destinationWarehouse,
            ReleaseMaterialPlanBuilder planBuilder) {
        this.orderViewService = Objects.requireNonNull(orderViewService, "orderViewService");
        this.foundationQuery = Objects.requireNonNull(foundationQuery, "foundationQuery");
        this.warehouseQuery = Objects.requireNonNull(warehouseQuery, "warehouseQuery");
        this.destinationWarehouse =
                Objects.requireNonNull(destinationWarehouse, "destinationWarehouse");
        this.planBuilder = Objects.requireNonNull(planBuilder, "planBuilder");
    }

    /**
     * Readiness for releasing the entire active production remainder of the order.
     */
    public MaterialReadinessResult evaluateOrderRemaining(SourceOrderId sourceOrderId) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        OrderProductionView view = orderViewService.getOrderProductionView(sourceOrderId);
        Optional<MaterialReadinessResult> notApplicable = notApplicableForViewStatus(view.status());
        if (notApplicable.isPresent()) {
            return notApplicable.get();
        }

        List<ProductionItemState> states = orderViewService.listItemStates(sourceOrderId);
        List<ItemReleaseQuantity> quantities = new ArrayList<>();
        for (ProductionItemState state : states) {
            long active = state.activeProductionQuantity().value().longValueExact();
            if (active > 0L) {
                quantities.add(new ItemReleaseQuantity(state.sourceOrderItemId().value(), active));
            }
        }
        if (quantities.isEmpty()) {
            return MaterialReadinessResult.notApplicable(
                    MaterialReadinessReason.NO_RELEASABLE_QUANTITY);
        }
        return evaluate(sourceOrderId, quantities, states);
    }

    /**
     * Readiness for an explicit future release quantity set (Phase 7 foundation).
     */
    public MaterialReadinessResult evaluateForRelease(
            SourceOrderId sourceOrderId, List<ItemReleaseQuantity> itemReleases) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(itemReleases, "itemReleases");
        if (itemReleases.isEmpty()) {
            throw new IllegalArgumentException("At least one item release quantity is required");
        }
        OrderProductionView view = orderViewService.getOrderProductionView(sourceOrderId);
        Optional<MaterialReadinessResult> notApplicable = notApplicableForViewStatus(view.status());
        if (notApplicable.isPresent()) {
            return notApplicable.get();
        }
        List<ProductionItemState> states = orderViewService.listItemStates(sourceOrderId);
        return evaluate(sourceOrderId, itemReleases, states);
    }

    private MaterialReadinessResult evaluate(
            SourceOrderId sourceOrderId,
            List<ItemReleaseQuantity> itemReleases,
            List<ProductionItemState> states) {
        Optional<UUID> productionWarehouseId = destinationWarehouse.findProductionWarehouseId();
        if (productionWarehouseId.isEmpty()) {
            return MaterialReadinessResult.noProductionWarehouse();
        }

        rejectDuplicateItems(itemReleases);
        Map<SourceOrderItemId, ProductionItemState> stateByItem = indexStatesByItem(states);

        List<MaterialReferenceEntry> materialCatalog = warehouseQuery.listMaterialReferences();
        Map<UUID, MaterialReferenceEntry> catalogById = indexCatalog(materialCatalog);

        Map<UUID, MutableDemand> demandByMaterial = new LinkedHashMap<>();
        try {
            for (ItemReleaseQuantity itemRelease : itemReleases) {
                SourceOrderItemId itemId = SourceOrderItemId.of(itemRelease.sourceOrderItemId());
                ProductionItemState state =
                        requireReleasableItem(stateByItem, itemId, itemRelease.releaseQuantity());
                List<ResolvedMaterialLine> specLines = foundationQuery.materialLines(state);
                List<PlannedMaterialLine> planned =
                        planBuilder.buildPlannedLines(
                                state,
                                itemRelease.releaseQuantity(),
                                specLines,
                                materialCatalog);
                for (PlannedMaterialLine line : planned) {
                    UUID materialId = line.materialReferenceId().value();
                    MutableDemand demand =
                            demandByMaterial.computeIfAbsent(
                                    materialId, ignored -> new MutableDemand());
                    demand.required = demand.required.add(line.plannedQuantity());
                    if (demand.materialName == null && line.materialName() != null) {
                        demand.materialName = line.materialName();
                    }
                }
            }
        } catch (ReleaseProductsException ex) {
            if (isUnresolvedOrAmbiguous(ex)) {
                return MaterialReadinessResult.unresolvedMaterial();
            }
            throw ex;
        } catch (FrozenSpecificationUnavailableException ex) {
            throw new ReleaseProductsException(
                    "Frozen specification unavailable for readiness of order "
                            + sourceOrderId.value(),
                    ex);
        }

        if (demandByMaterial.isEmpty()) {
            return new MaterialReadinessResult(
                    MaterialReadinessStatus.READY,
                    MaterialReadinessReason.NONE,
                    0,
                    List.of());
        }

        Map<UUID, BigDecimal> availableByMaterial =
                warehouseQuery.availableQuantities(
                        productionWarehouseId.get(), demandByMaterial.keySet());

        List<MaterialReadinessLine> lines = new ArrayList<>(demandByMaterial.size());
        int deficient = 0;
        for (Map.Entry<UUID, MutableDemand> entry : demandByMaterial.entrySet()) {
            UUID materialId = entry.getKey();
            MutableDemand demand = entry.getValue();
            MaterialReferenceEntry catalog = catalogById.get(materialId);
            BigDecimal available =
                    availableByMaterial.getOrDefault(materialId, BigDecimal.ZERO);
            BigDecimal shortage = shortage(demand.required, available);
            if (shortage.signum() > 0) {
                deficient++;
            }
            lines.add(
                    new MaterialReadinessLine(
                            materialId,
                            catalog == null ? "" : catalog.article(),
                            resolveName(demand.materialName, catalog),
                            catalog == null ? "" : catalog.color(),
                            catalog == null ? "" : catalog.unitOfMeasure(),
                            demand.required,
                            available,
                            shortage));
        }

        if (deficient > 0) {
            return new MaterialReadinessResult(
                    MaterialReadinessStatus.NOT_READY,
                    MaterialReadinessReason.INSUFFICIENT_STOCK,
                    deficient,
                    lines);
        }
        return new MaterialReadinessResult(
                MaterialReadinessStatus.READY, MaterialReadinessReason.NONE, 0, lines);
    }

    private static Optional<MaterialReadinessResult> notApplicableForViewStatus(
            OrderProductionViewStatus status) {
        return switch (status) {
            case NOT_ACCEPTED ->
                    Optional.of(
                            MaterialReadinessResult.notApplicable(
                                    MaterialReadinessReason.NOT_ACCEPTED));
            case MANUFACTURED ->
                    Optional.of(
                            MaterialReadinessResult.notApplicable(
                                    MaterialReadinessReason.MANUFACTURED));
            case CANCELLED ->
                    Optional.of(
                            MaterialReadinessResult.notApplicable(
                                    MaterialReadinessReason.CANCELLED));
            case IN_PRODUCTION -> Optional.empty();
        };
    }

    private static ProductionItemState requireReleasableItem(
            Map<SourceOrderItemId, ProductionItemState> stateByItem,
            SourceOrderItemId itemId,
            long releaseQuantity) {
        ProductionItemState state = stateByItem.get(itemId);
        if (state == null) {
            throw new ReleaseProductsException(
                    "Production item state not found: " + itemId.value());
        }
        if (state.status() != ProductionStatus.IN_PRODUCTION
                && state.status() != ProductionStatus.PARTIALLY_RELEASED) {
            throw new ReleaseProductsException(
                    "Release rejected for item status " + state.status() + ": " + itemId.value());
        }
        if (state.activeProductionQuantity().value().longValueExact() < releaseQuantity) {
            throw new ReleaseProductsException(
                    "Release quantity exceeds active production quantity for item "
                            + itemId.value());
        }
        return state;
    }

    private static Map<SourceOrderItemId, ProductionItemState> indexStatesByItem(
            List<ProductionItemState> states) {
        Map<SourceOrderItemId, ProductionItemState> byItem = new HashMap<>();
        for (ProductionItemState state : states) {
            ProductionItemState previous = byItem.put(state.sourceOrderItemId(), state);
            if (previous != null) {
                throw new ReleaseProductsException(
                        "Ambiguous Production item state for item "
                                + state.sourceOrderItemId().value());
            }
        }
        return byItem;
    }

    private static Map<UUID, MaterialReferenceEntry> indexCatalog(
            List<MaterialReferenceEntry> catalog) {
        Map<UUID, MaterialReferenceEntry> byId = new HashMap<>();
        for (MaterialReferenceEntry entry : catalog) {
            byId.put(entry.materialReferenceId(), entry);
        }
        return byId;
    }

    private static void rejectDuplicateItems(List<ItemReleaseQuantity> itemReleases) {
        Set<UUID> seen = new HashSet<>();
        for (ItemReleaseQuantity release : itemReleases) {
            if (!seen.add(release.sourceOrderItemId())) {
                throw new IllegalArgumentException(
                        "Duplicate sourceOrderItemId in readiness request: "
                                + release.sourceOrderItemId());
            }
        }
    }

    private static boolean isUnresolvedOrAmbiguous(ReleaseProductsException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        return message.contains("MATERIAL_UNRESOLVED") || message.contains("MATERIAL_AMBIGUOUS");
    }

    private static BigDecimal shortage(BigDecimal required, BigDecimal available) {
        BigDecimal difference = required.subtract(available);
        return difference.signum() > 0 ? difference : BigDecimal.ZERO;
    }

    private static String resolveName(String plannedName, MaterialReferenceEntry catalog) {
        if (plannedName != null && !plannedName.isBlank()) {
            return plannedName;
        }
        return catalog == null ? "" : catalog.name();
    }

    /** Explicit release quantity for one Order Item. */
    public record ItemReleaseQuantity(UUID sourceOrderItemId, long releaseQuantity) {

        public ItemReleaseQuantity {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            if (releaseQuantity <= 0L) {
                throw new IllegalArgumentException("releaseQuantity must be > 0");
            }
        }
    }

    private static final class MutableDemand {
        private String materialName;
        private BigDecimal required = BigDecimal.ZERO;
    }
}
