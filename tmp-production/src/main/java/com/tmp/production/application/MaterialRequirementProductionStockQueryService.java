package com.tmp.production.application;

import com.tmp.production.application.MaterialReferenceResolver.CatalogEntry;
import com.tmp.production.application.MaterialReferenceResolver.ResolutionStatus;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.MaterialReferenceEntry;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Live read-side AVAILABLE snapshot on the Material Requirement destination (production) warehouse.
 *
 * <p>Informational only: does not subtract from request quantity, does not reserve stock, and is
 * not persisted on the Material Requirement aggregate.
 */
public final class MaterialRequirementProductionStockQueryService {

    private final WarehouseAvailabilityQueryPort warehouseQuery;
    private final MaterialReferenceResolver materialReferenceResolver;

    public MaterialRequirementProductionStockQueryService(
            WarehouseAvailabilityQueryPort warehouseQuery) {
        this(warehouseQuery, new MaterialReferenceResolver());
    }

    MaterialRequirementProductionStockQueryService(
            WarehouseAvailabilityQueryPort warehouseQuery,
            MaterialReferenceResolver materialReferenceResolver) {
        this.warehouseQuery = Objects.requireNonNull(warehouseQuery, "warehouseQuery");
        this.materialReferenceResolver =
                Objects.requireNonNull(materialReferenceResolver, "materialReferenceResolver");
    }

    /**
     * Returns one entry per requirement line in document order.
     *
     * <p>{@code onProductionAvailable} is {@code null} when the Specification material is
     * unmatched or ambiguous against Warehouse MaterialReference. When uniquely resolved, the
     * value is the summed AVAILABLE quantity across production-warehouse cells (including {@code
     * 0}).
     */
    public List<LineProductionStock> stockFor(MaterialRequirement requirement) {
        Objects.requireNonNull(requirement, "requirement");
        UUID warehouseId = requirement.destinationWarehouseId();
        List<MaterialReferenceEntry> catalog = warehouseQuery.listMaterialReferences();
        List<CatalogEntry> resolverCatalog = toCatalog(catalog);

        Map<UUID, UUID> resolvedMaterialByLineId = new LinkedHashMap<>();
        Set<UUID> resolvedMaterialIds = new HashSet<>();
        for (MaterialRequirementLine line : requirement.lines()) {
            MaterialReferenceResolver.Result resolution =
                    materialReferenceResolver.resolve(line.identity(), resolverCatalog);
            if (resolution.status() == ResolutionStatus.RESOLVED) {
                UUID materialId = resolution.materialReferenceId();
                resolvedMaterialByLineId.put(line.lineId().value(), materialId);
                resolvedMaterialIds.add(materialId);
            }
        }

        Map<UUID, BigDecimal> availableByMaterial =
                resolvedMaterialIds.isEmpty()
                        ? Map.of()
                        : warehouseQuery.availableQuantities(warehouseId, resolvedMaterialIds);

        List<LineProductionStock> rows = new ArrayList<>(requirement.lines().size());
        for (MaterialRequirementLine line : requirement.lines()) {
            UUID lineId = line.lineId().value();
            UUID materialId = resolvedMaterialByLineId.get(lineId);
            if (materialId == null) {
                rows.add(new LineProductionStock(lineId, null));
            } else {
                rows.add(
                        new LineProductionStock(
                                lineId,
                                availableByMaterial.getOrDefault(materialId, BigDecimal.ZERO)));
            }
        }
        return List.copyOf(rows);
    }

    private static List<CatalogEntry> toCatalog(List<MaterialReferenceEntry> catalog) {
        List<CatalogEntry> entries = new ArrayList<>(catalog.size());
        for (MaterialReferenceEntry entry : catalog) {
            entries.add(
                    new CatalogEntry(
                            entry.materialReferenceId(),
                            entry.article(),
                            entry.color(),
                            entry.unitOfMeasure()));
        }
        return entries;
    }

    /**
     * @param onProductionAvailable {@code null} means unmatched/ambiguous (UI shows "—");
     *     non-null includes zero stock for a uniquely resolved MaterialReference
     */
    public record LineProductionStock(UUID lineId, BigDecimal onProductionAvailable) {
        public LineProductionStock {
            Objects.requireNonNull(lineId, "lineId");
        }

        public boolean resolved() {
            return onProductionAvailable != null;
        }
    }
}
