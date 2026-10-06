package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.WarehouseDemand;
import com.tmp.warehouse.domain.WarehouseDemandId;
import com.tmp.warehouse.domain.WarehouseDemandLineId;
import com.tmp.warehouse.domain.WarehouseDemandTransferLink;
import com.tmp.warehouse.domain.WarehouseDemandWaitingReason;
import com.tmp.warehouse.domain.WarehouseTransferLineId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Warehouse-owned persistence port for the Demand aggregate (B3B-1). Internal to Warehouse.
 */
public interface WarehouseDemandRepository {

    void insert(WarehouseDemand demand);

    Optional<WarehouseDemand> findById(WarehouseDemandId demandId);

    Optional<WarehouseDemand> findBySourceMaterialRequirementId(UUID sourceMaterialRequirementId);

    /**
     * Non-cancelled Demands with lines (B3B-3C1 inbox batch). Does not load Transfer links or
     * history — fulfillment facts are joined separately.
     */
    List<WarehouseDemand> findAllNonCancelled();

    /**
     * Loads the demand header with {@code SELECT … FOR UPDATE} for future cancel/receive/routing
     * serialization.
     */
    Optional<WarehouseDemand> lockById(WarehouseDemandId demandId);

    void insertTransferLink(WarehouseDemandTransferLink link);

    /**
     * Inserts a transfer link when absent. Idempotent under {@code UNIQUE(transfer_line_id)} —
     * duplicate replay does not corrupt Demand lineage.
     *
     * @return {@code true} if a row was inserted; {@code false} if the transfer line was already
     *     linked
     */
    boolean insertTransferLinkIfAbsent(WarehouseDemandTransferLink link);

    List<WarehouseDemandTransferLink> findTransferLinksByDemandLineId(
            WarehouseDemandLineId demandLineId);

    List<WarehouseDemandTransferLink> findTransferLinksByDemandId(WarehouseDemandId demandId);

    Optional<WarehouseDemandTransferLink> findTransferLinkByTransferLineId(
            WarehouseTransferLineId transferLineId);

    /**
     * Updates mutable operational resolution on a Demand line ({@code materialReferenceId}, {@code
     * waitingReason}). Snapshot identity fields are never changed.
     */
    void updateLineOperationalResolution(
            WarehouseDemandLineId demandLineId,
            MaterialReferenceId materialReferenceId,
            WarehouseDemandWaitingReason waitingReason);
}
