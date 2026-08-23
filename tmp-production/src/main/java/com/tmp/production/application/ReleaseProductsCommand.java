package com.tmp.production.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** User-facing command for the Release Products orchestration use case. */
public record ReleaseProductsCommand(
        UUID sourceOrderId, List<ItemRelease> itemReleases, List<MaterialActualUsage> materialActualUsages) {

    public ReleaseProductsCommand {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(itemReleases, "itemReleases");
        Objects.requireNonNull(materialActualUsages, "materialActualUsages");
        if (itemReleases.isEmpty()) {
            throw new IllegalArgumentException("At least one item release is required");
        }
        itemReleases = List.copyOf(itemReleases);
        materialActualUsages = List.copyOf(materialActualUsages);
    }

    public record ItemRelease(UUID sourceOrderItemId, long releaseQuantity) {

        public ItemRelease {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            if (releaseQuantity <= 0) {
                throw new IllegalArgumentException("releaseQuantity must be > 0");
            }
        }
    }

    public record MaterialActualUsage(
            UUID sourceOrderItemId,
            UUID materialReferenceId,
            BigDecimal actualQuantity,
            List<CellAllocation> allocations) {

        public MaterialActualUsage {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(actualQuantity, "actualQuantity");
            Objects.requireNonNull(allocations, "allocations");
            if (actualQuantity.signum() < 0) {
                throw new IllegalArgumentException("actualQuantity must be >= 0");
            }
            allocations = List.copyOf(allocations);
            if (actualQuantity.signum() == 0 && !allocations.isEmpty()) {
                throw new IllegalArgumentException(
                        "Zero actualQuantity requires empty allocations for material "
                                + materialReferenceId);
            }
            for (CellAllocation allocation : allocations) {
                if (allocation.quantity().signum() <= 0) {
                    throw new IllegalArgumentException("allocation quantity must be > 0");
                }
            }
        }

        public String stableKey() {
            return sourceOrderItemId + "|" + materialReferenceId;
        }
    }

    public record CellAllocation(UUID storageCellId, BigDecimal quantity) {

        public CellAllocation {
            Objects.requireNonNull(storageCellId, "storageCellId");
            Objects.requireNonNull(quantity, "quantity");
        }
    }
}
