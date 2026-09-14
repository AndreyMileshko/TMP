package com.tmp.production.application;

import com.tmp.production.domain.InvalidProductionDestinationWarehouseException;
import com.tmp.warehouse.api.WarehouseReferenceQueryApi;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Resolves the Production destination warehouse from Warehouse-managed assignment.
 *
 * <p>Not a configuration UUID holder. Source of truth is Warehouse {@code is_production} /
 * {@link WarehouseReferenceQueryApi#findProductionWarehouse()}.
 */
public final class ProductionDestinationWarehouse {

    private final Supplier<Optional<UUID>> resolver;

    public ProductionDestinationWarehouse(Supplier<Optional<UUID>> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /** Fixed destination for tests / explicit beans. */
    public ProductionDestinationWarehouse(UUID productionWarehouseId) {
        Objects.requireNonNull(productionWarehouseId, "productionWarehouseId");
        this.resolver = () -> Optional.of(productionWarehouseId);
    }

    public static ProductionDestinationWarehouse fromWarehouseReferences(
            WarehouseReferenceQueryApi warehouseReferences) {
        Objects.requireNonNull(warehouseReferences, "warehouseReferences");
        return new ProductionDestinationWarehouse(
                () ->
                        warehouseReferences
                                .findProductionWarehouse()
                                .map(view -> view.warehouseId()));
    }

    /** Current production destination when assigned. */
    public Optional<UUID> findProductionWarehouseId() {
        return resolver.get();
    }

    /**
     * Required production destination id for material availability / Material Requirement / release.
     *
     * @throws InvalidProductionDestinationWarehouseException when none assigned
     */
    public UUID productionWarehouseId() {
        return findProductionWarehouseId()
                .orElseThrow(InvalidProductionDestinationWarehouseException::notAssigned);
    }
}
