package com.tmp.production.domain.repository;

import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialRequirementId;
import com.tmp.production.domain.MaterialRequirementLineId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Production-owned persistence port for Material Requirement Submit traceability (Stage 3.5.10):
 * generated Warehouse Transfer Document links + immutable routing snapshot. UUIDs are stored as
 * external references only (no FK to Warehouse / Document Engine schema).
 *
 * <p>All methods participate in the caller's Submit transaction.
 */
public interface MaterialRequirementSubmissionRepository {

    /** Persists the generated-document links (one per source warehouse) for a Submit. */
    void saveGeneratedDocuments(
            MaterialRequirementId requirementId, List<GeneratedDocumentLink> documents);

    /** Persists the immutable routing snapshot (one row per requirement line). */
    void saveRoutingSnapshot(
            MaterialRequirementId requirementId, List<RoutingSnapshotRow> snapshot);

    List<GeneratedDocumentLink> findGeneratedDocuments(MaterialRequirementId requirementId);

    List<RoutingSnapshotRow> findRoutingSnapshot(MaterialRequirementId requirementId);

    /** One generated DRAFT Warehouse Transfer Document link. */
    record GeneratedDocumentLink(
            UUID warehouseDocumentId,
            UUID sourceWarehouseId,
            UUID destinationWarehouseId,
            int documentOrder,
            Instant createdAt) {
        public GeneratedDocumentLink {
            Objects.requireNonNull(warehouseDocumentId, "warehouseDocumentId");
            Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
            Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    /** One routing snapshot row (audit/planning fact at Submit time). */
    record RoutingSnapshotRow(
            MaterialRequirementLineId requirementLineId,
            MaterialReferenceId materialReferenceId,
            UUID sourceWarehouseId,
            String sourceWarehouseCode,
            UUID warehouseDocumentId,
            UUID warehouseTransferLineId,
            BigDecimal availableAtRouting,
            BigDecimal routedQuantity,
            BigDecimal uncoveredQuantity) {
        public RoutingSnapshotRow {
            Objects.requireNonNull(requirementLineId, "requirementLineId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
            Objects.requireNonNull(sourceWarehouseCode, "sourceWarehouseCode");
            Objects.requireNonNull(warehouseDocumentId, "warehouseDocumentId");
            Objects.requireNonNull(warehouseTransferLineId, "warehouseTransferLineId");
            Objects.requireNonNull(availableAtRouting, "availableAtRouting");
            Objects.requireNonNull(routedQuantity, "routedQuantity");
            Objects.requireNonNull(uncoveredQuantity, "uncoveredQuantity");
        }
    }
}
