package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.WarehouseDemand;
import com.tmp.warehouse.domain.WarehouseDemandId;
import com.tmp.warehouse.domain.WarehouseDemandLineId;
import com.tmp.warehouse.domain.WarehouseDemandTransferLink;
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
     * Loads the demand header with {@code SELECT … FOR UPDATE} for future cancel/receive/routing
     * serialization.
     */
    Optional<WarehouseDemand> lockById(WarehouseDemandId demandId);

    void insertTransferLink(WarehouseDemandTransferLink link);

    List<WarehouseDemandTransferLink> findTransferLinksByDemandLineId(
            WarehouseDemandLineId demandLineId);
}
