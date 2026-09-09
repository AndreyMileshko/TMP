package com.tmp.warehouse.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Warehouse-owned inter-capability demand command contract (ADR-037 §D/§E / Stage 3.5.10).
 *
 * <p>Trusted backend orchestration boundary: given a destination warehouse and abstract material
 * demand lines, Warehouse performs automatic source routing (reusing {@link
 * WarehouseQueryApi#routeMaterials}), groups the demand by selected source warehouse and creates
 * one DRAFT Transfer Document per source warehouse. The created documents surface through the
 * existing operational inbox as {@code TRANSFER_PREPARATION} tasks.
 *
 * <p>This API is intentionally NOT part of {@link WarehouseCommandApi} and is NOT a user-facing
 * Warehouse operation. It must not be invoked from UI. It does NOT check {@link
 * com.tmp.warehouse.application.WarehouseResponsibilityGuard} and does NOT require {@code
 * warehouse.transfer}: the calling capability owns its own authorization. It does not mutate stock,
 * warehouse operations or movements, and it does not manage its own idempotency — idempotency of
 * the surrounding business operation belongs to the caller.
 *
 * <p>Created Transfer Document lines always carry the FULL requested demand quantity, never the
 * routed quantity. Physical partial fulfilment / shortfall continuation remains Stage 3.5.7 at
 * actual send time; the returned {@link RoutedLine#routedQuantity()} / {@link
 * RoutedLine#uncoveredQuantity()} are an audit/planning snapshot only.
 */
public interface WarehouseDemandCommandApi {

    /**
     * Routes every demand line, then (only if all lines resolved to a source) groups by source
     * warehouse and creates one DRAFT Transfer Document per source. Must be called inside the
     * caller's transaction; participates in it (PROPAGATION_REQUIRED).
     *
     * @throws DemandSourceUnavailableException if any line has no positive-AVAILABLE source
     * @throws IllegalArgumentException if the destination is missing, or a material is unknown
     * @throws com.tmp.warehouse.domain.InvalidWarehouseStateException if the destination is inactive
     */
    RoutedTransferResult createRoutedTransferDocuments(CreateRoutedTransferCommand command);

    /** Inter-capability demand: destination warehouse + abstract demand lines. */
    record CreateRoutedTransferCommand(UUID destinationWarehouseId, List<DemandLine> lines) {
        public CreateRoutedTransferCommand {
            Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    /**
     * One abstract demand line. {@code demandKey} correlates the caller's line to routing results
     * (Stage 3.5.10: the Material Requirement line id as a UUID-safe string). Duplicate material
     * ids are not merged.
     */
    record DemandLine(String demandKey, UUID materialReferenceId, BigDecimal quantity) {
        public DemandLine {
            Objects.requireNonNull(demandKey, "demandKey");
            if (demandKey.isBlank()) {
                throw new IllegalArgumentException("demandKey must not be blank");
            }
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(quantity, "quantity");
            if (quantity.signum() <= 0) {
                throw new IllegalArgumentException("quantity must be positive: " + quantity);
            }
        }
    }

    /** Traceability returned to the caller: generated documents + per-line routing snapshot. */
    record RoutedTransferResult(List<GeneratedDocument> documents, List<RoutedLine> routing) {
        public RoutedTransferResult {
            documents = documents == null ? List.of() : List.copyOf(documents);
            routing = routing == null ? List.of() : List.copyOf(routing);
        }
    }

    /** One generated DRAFT Transfer Document reference (no Warehouse internals exposed). */
    record GeneratedDocument(
            UUID documentId, UUID sourceWarehouseId, UUID destinationWarehouseId) {
        public GeneratedDocument {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
            Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        }
    }

    /**
     * Per-line routing/creation snapshot at Submit time. {@code routedQuantity} /
     * {@code uncoveredQuantity} are audit-only and do NOT change the created document line quantity
     * (which equals the full requested {@code quantity} via the demand line).
     */
    record RoutedLine(
            String demandKey,
            UUID materialReferenceId,
            UUID sourceWarehouseId,
            String sourceWarehouseCode,
            BigDecimal availableAtRouting,
            BigDecimal routedQuantity,
            BigDecimal uncoveredQuantity,
            UUID warehouseDocumentId,
            UUID warehouseTransferLineId) {
        public RoutedLine {
            Objects.requireNonNull(demandKey, "demandKey");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
            Objects.requireNonNull(sourceWarehouseCode, "sourceWarehouseCode");
            Objects.requireNonNull(availableAtRouting, "availableAtRouting");
            Objects.requireNonNull(routedQuantity, "routedQuantity");
            Objects.requireNonNull(uncoveredQuantity, "uncoveredQuantity");
            Objects.requireNonNull(warehouseDocumentId, "warehouseDocumentId");
            Objects.requireNonNull(warehouseTransferLineId, "warehouseTransferLineId");
        }
    }
}
