package com.tmp.order.application.imports;

import java.util.Optional;

/**
 * Persistence port for capability-owned order import metadata. Only the JDBC adapter may use
 * JDBC against the metadata table; business aggregates are never written here.
 */
public interface OrderImportMetadataRepository {

    boolean existsBySourceTypeAndChecksum(String sourceType, String contentChecksum);

    Optional<OrderImportMetadata> findBySourceTypeAndChecksum(
            String sourceType, String contentChecksum);

    OrderImportMetadata save(OrderImportMetadata metadata);
}
