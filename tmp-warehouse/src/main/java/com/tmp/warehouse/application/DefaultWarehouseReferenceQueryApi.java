package com.tmp.warehouse.application;

import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceView;
import com.tmp.warehouse.api.WarehouseReferenceQueryApi;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default {@link WarehouseReferenceQueryApi}: repository reads without Warehouse user RBAC.
 *
 * <p>Caller capability owns authorization. No stock / routing / responsibility side effects.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-injected repository collaborators.")
public final class DefaultWarehouseReferenceQueryApi implements WarehouseReferenceQueryApi {

    private final WarehouseCatalogRepository warehouses;
    private final MaterialReferenceRepository materials;

    public DefaultWarehouseReferenceQueryApi(
            WarehouseCatalogRepository warehouses, MaterialReferenceRepository materials) {
        this.warehouses = Objects.requireNonNull(warehouses, "warehouses");
        this.materials = Objects.requireNonNull(materials, "materials");
    }

    @Override
    public Optional<WarehouseReferenceView> getWarehouseReference(UUID warehouseId) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        WarehouseId id = WarehouseId.of(warehouseId);
        return warehouses.findAll().stream()
                .filter(warehouse -> warehouse.id().equals(id))
                .findFirst()
                .map(this::toView);
    }

    @Override
    public List<MaterialReferenceView> findMaterialReferencesByIdentity(
            String article, String color, String unitOfMeasure) {
        Objects.requireNonNull(article, "article");
        Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        String articleKey = article.trim();
        String colorKey = normalizeColor(color);
        String unitKey = unitOfMeasure.trim();
        return materials.findAll().stream()
                .filter(
                        material ->
                                material.article().equals(articleKey)
                                        && normalizeColor(material.color()).equals(colorKey)
                                        && material.unitOfMeasure().trim().equals(unitKey))
                .map(this::toView)
                .toList();
    }

    private WarehouseReferenceView toView(Warehouse warehouse) {
        return new WarehouseReferenceView(
                warehouse.id().value(), warehouse.code(), warehouse.name(), warehouse.active());
    }

    private MaterialReferenceView toView(MaterialReference material) {
        return new MaterialReferenceView(
                material.id().value(),
                material.article(),
                material.name(),
                material.color(),
                material.size(),
                material.unitOfMeasure());
    }

    private static String normalizeColor(String color) {
        return color == null ? "" : color.trim();
    }
}
