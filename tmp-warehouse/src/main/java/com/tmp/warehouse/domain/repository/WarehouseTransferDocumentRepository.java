package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.TransferDocumentOptimisticLockException;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Warehouse-owned persistence port for Transfer Document payload (ADR-028). Internal to Warehouse.
 */
public interface WarehouseTransferDocumentRepository {

    void insert(WarehouseTransferDocument document);

    Optional<WarehouseTransferDocument> findByDocumentId(UUID documentId);

    /**
     * Batch load payloads keyed by document id. Missing keys are omitted (no empty placeholders).
     */
    Map<UUID, WarehouseTransferDocument> findByDocumentIds(Collection<UUID> documentIds);

    /**
     * Replaces header warehouses and lines when {@code expectedPayloadRevision} matches.
     *
     * @throws TransferDocumentOptimisticLockException when revision is stale
     */
    void update(WarehouseTransferDocument document, long expectedPayloadRevision);

    void deleteByDocumentId(UUID documentId);

    boolean existsByDocumentId(UUID documentId);
}
