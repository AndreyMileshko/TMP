package com.tmp.warehouse.application;

import com.tmp.warehouse.api.WarehouseApi.MaterialDemand;
import com.tmp.warehouse.api.WarehouseApi.MaterialSourceRoutingOutcome;
import com.tmp.warehouse.api.WarehouseApi.MaterialSourceRoutingResult;
import com.tmp.warehouse.api.WarehouseApi.SourceCellSuggestion;
import com.tmp.warehouse.domain.repository.AvailableStockAggregationQuery;
import com.tmp.warehouse.domain.repository.AvailableStockAggregationQuery.AvailableCellStock;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Automatic source warehouse routing and source-cell suggestion (ADR-037 / Stage 3.5.4).
 *
 * <p>Query/planning only: does not mutate stock, create reservations, operations, movements, or
 * transfer documents. Does not apply warehouse responsibility filters (global AVAILABLE search).
 * Authorization is the caller's responsibility.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected AvailableStockAggregationQuery.")
public final class MaterialSourceRoutingService {

    private static final Comparator<WarehouseCandidate> WAREHOUSE_SELECTION =
            Comparator.comparing(WarehouseCandidate::available)
                    .reversed()
                    .thenComparing(WarehouseCandidate::warehouseCode)
                    .thenComparing(WarehouseCandidate::warehouseId);

    private static final Comparator<AvailableCellStock> CELL_SELECTION =
            Comparator.comparing(AvailableCellStock::availableQuantity)
                    .reversed()
                    .thenComparing(AvailableCellStock::storageCellCode)
                    .thenComparing(AvailableCellStock::storageCellId);

    private final AvailableStockAggregationQuery availableStock;

    public MaterialSourceRoutingService(AvailableStockAggregationQuery availableStock) {
        this.availableStock = Objects.requireNonNull(availableStock, "availableStock");
    }

    public List<MaterialSourceRoutingResult> routeMaterials(
            UUID destinationWarehouseId, List<MaterialDemand> demands) {
        Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        Objects.requireNonNull(demands, "demands");

        List<UUID> materialIds = new ArrayList<>(demands.size());
        for (MaterialDemand demand : demands) {
            Objects.requireNonNull(demand, "demand");
            requirePositiveQuantity(demand.quantity());
            materialIds.add(demand.materialReferenceId());
        }

        List<AvailableCellStock> rows = availableStock.findAvailableByMaterials(materialIds);
        Map<UUID, List<AvailableCellStock>> byMaterial = new HashMap<>();
        for (AvailableCellStock row : rows) {
            if (row.warehouseId().equals(destinationWarehouseId)) {
                continue;
            }
            byMaterial.computeIfAbsent(row.materialReferenceId(), ignored -> new ArrayList<>()).add(row);
        }

        List<MaterialSourceRoutingResult> results = new ArrayList<>(demands.size());
        for (MaterialDemand demand : demands) {
            results.add(routeOne(demand, byMaterial.getOrDefault(demand.materialReferenceId(), List.of())));
        }
        return List.copyOf(results);
    }

    public MaterialSourceRoutingResult routeMaterial(
            UUID destinationWarehouseId, UUID materialReferenceId, BigDecimal quantity) {
        Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        return routeMaterials(
                        destinationWarehouseId,
                        List.of(new MaterialDemand("1", materialReferenceId, quantity)))
                .get(0);
    }

    private static MaterialSourceRoutingResult routeOne(
            MaterialDemand demand, List<AvailableCellStock> materialRows) {
        Map<UUID, WarehouseCandidate> candidates = aggregateByWarehouse(materialRows);
        if (candidates.isEmpty()) {
            return noSource(demand);
        }

        List<WarehouseCandidate> complete = new ArrayList<>();
        List<WarehouseCandidate> positive = new ArrayList<>();
        for (WarehouseCandidate candidate : candidates.values()) {
            if (candidate.available().compareTo(demand.quantity()) >= 0) {
                complete.add(candidate);
            }
            if (candidate.available().signum() > 0) {
                positive.add(candidate);
            }
        }

        WarehouseCandidate selected;
        if (!complete.isEmpty()) {
            complete.sort(WAREHOUSE_SELECTION);
            selected = complete.get(0);
        } else if (!positive.isEmpty()) {
            positive.sort(WAREHOUSE_SELECTION);
            selected = positive.get(0);
        } else {
            return noSource(demand);
        }

        BigDecimal routed = selected.available().min(demand.quantity());
        BigDecimal uncovered = demand.quantity().subtract(routed);
        List<SourceCellSuggestion> suggestions =
                suggestCells(
                        materialRows.stream()
                                .filter(row -> row.warehouseId().equals(selected.warehouseId()))
                                .toList(),
                        routed);

        return new MaterialSourceRoutingResult(
                demand.demandKey(),
                demand.materialReferenceId(),
                demand.quantity(),
                MaterialSourceRoutingOutcome.SOURCE_SELECTED,
                selected.warehouseId(),
                selected.warehouseCode(),
                selected.available(),
                routed,
                uncovered,
                suggestions);
    }

    private static Map<UUID, WarehouseCandidate> aggregateByWarehouse(
            List<AvailableCellStock> materialRows) {
        Map<UUID, WarehouseCandidate> candidates = new LinkedHashMap<>();
        for (AvailableCellStock row : materialRows) {
            WarehouseCandidate existing = candidates.get(row.warehouseId());
            if (existing == null) {
                candidates.put(
                        row.warehouseId(),
                        new WarehouseCandidate(
                                row.warehouseId(),
                                row.warehouseCode(),
                                row.availableQuantity()));
            } else {
                candidates.put(
                        row.warehouseId(),
                        new WarehouseCandidate(
                                existing.warehouseId(),
                                existing.warehouseCode(),
                                existing.available().add(row.availableQuantity())));
            }
        }
        return candidates;
    }

    private static List<SourceCellSuggestion> suggestCells(
            List<AvailableCellStock> cells, BigDecimal routedQuantity) {
        if (routedQuantity.signum() <= 0 || cells.isEmpty()) {
            return List.of();
        }
        List<AvailableCellStock> ordered = new ArrayList<>(cells);
        ordered.sort(CELL_SELECTION);

        List<SourceCellSuggestion> suggestions = new ArrayList<>();
        BigDecimal remaining = routedQuantity;
        for (AvailableCellStock cell : ordered) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal take = cell.availableQuantity().min(remaining);
            if (take.signum() <= 0) {
                continue;
            }
            suggestions.add(
                    new SourceCellSuggestion(
                            cell.storageCellId(),
                            cell.storageCellCode(),
                            cell.availableQuantity(),
                            take));
            remaining = remaining.subtract(take);
        }
        return List.copyOf(suggestions);
    }

    private static MaterialSourceRoutingResult noSource(MaterialDemand demand) {
        return new MaterialSourceRoutingResult(
                demand.demandKey(),
                demand.materialReferenceId(),
                demand.quantity(),
                MaterialSourceRoutingOutcome.NO_AVAILABLE_SOURCE,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                demand.quantity(),
                List.of());
    }

    private static void requirePositiveQuantity(BigDecimal quantity) {
        Objects.requireNonNull(quantity, "quantity");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
    }

    private record WarehouseCandidate(UUID warehouseId, String warehouseCode, BigDecimal available) {}
}
