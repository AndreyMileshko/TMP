package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.WarehouseDemandId;
import com.tmp.warehouse.domain.WarehouseDemandLineId;
import com.tmp.warehouse.domain.WarehouseDemandResupplyWaiver;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistence port for Warehouse-owned Demand resupply-waiver ledger events. */
public interface WarehouseDemandResupplyWaiverRepository {

    void insert(WarehouseDemandResupplyWaiver waiver);

    /**
     * Inserts when absent under {@code UNIQUE(transfer_document_id, demand_line_id)}.
     *
     * @return {@code true} if inserted; {@code false} if the unique key already existed
     */
    boolean insertIfAbsent(WarehouseDemandResupplyWaiver waiver);

    Map<WarehouseDemandLineId, BigDecimal> waivedQuantitiesByDemandLine(WarehouseDemandId demandId);

    Map<WarehouseDemandLineId, BigDecimal> waivedQuantitiesByDemandLines(Collection<UUID> demandIds);

    List<WarehouseDemandResupplyWaiver> findByTransferDocumentId(UUID transferDocumentId);

    List<WarehouseDemandResupplyWaiver> findByDemandLineId(WarehouseDemandLineId demandLineId);
}