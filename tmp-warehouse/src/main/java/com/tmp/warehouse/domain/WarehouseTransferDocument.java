package com.tmp.warehouse.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Warehouse-owned multi-line Transfer Document payload (Stage 3.5.2 / ADR-037 / ADR-028).
 *
 * <p>Keyed by Document Engine {@code DocumentId}. Editability is gated by Document Engine DRAFT
 * status (not duplicated here). {@code payloadRevision} is the payload optimistic lock.
 */
public final class WarehouseTransferDocument {

    public static final int SCHEMA_VERSION = 1;

    private final UUID documentId;
    private final WarehouseId sourceWarehouseId;
    private final WarehouseId destinationWarehouseId;
    private final int payloadSchemaVersion;
    private final long payloadRevision;
    private final List<WarehouseTransferLine> lines;

    private WarehouseTransferDocument(
            UUID documentId,
            WarehouseId sourceWarehouseId,
            WarehouseId destinationWarehouseId,
            int payloadSchemaVersion,
            long payloadRevision,
            List<WarehouseTransferLine> lines) {
        this.documentId = documentId;
        this.sourceWarehouseId = sourceWarehouseId;
        this.destinationWarehouseId = destinationWarehouseId;
        this.payloadSchemaVersion = payloadSchemaVersion;
        this.payloadRevision = payloadRevision;
        this.lines = List.copyOf(lines);
    }

    public static WarehouseTransferDocument create(
            UUID documentId,
            WarehouseId sourceWarehouseId,
            WarehouseId destinationWarehouseId,
            List<WarehouseTransferLine> lines) {
        return of(documentId, sourceWarehouseId, destinationWarehouseId, SCHEMA_VERSION, 0L, lines);
    }

    public static WarehouseTransferDocument of(
            UUID documentId,
            WarehouseId sourceWarehouseId,
            WarehouseId destinationWarehouseId,
            int payloadSchemaVersion,
            long payloadRevision,
            List<WarehouseTransferLine> lines) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
        Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        Objects.requireNonNull(lines, "lines");
        if (sourceWarehouseId.equals(destinationWarehouseId)) {
            throw new InvalidWarehouseStateException(
                    "Transfer document requires distinct warehouses: warehouseId="
                            + sourceWarehouseId);
        }
        if (payloadSchemaVersion < 1) {
            throw new IllegalArgumentException(
                    "payloadSchemaVersion must be >= 1: " + payloadSchemaVersion);
        }
        if (payloadRevision < 0) {
            throw new IllegalArgumentException(
                    "payloadRevision must not be negative: " + payloadRevision);
        }
        validateLines(lines);
        return new WarehouseTransferDocument(
                documentId,
                sourceWarehouseId,
                destinationWarehouseId,
                payloadSchemaVersion,
                payloadRevision,
                lines);
    }

    public WarehouseTransferDocument withContent(
            WarehouseId newSourceWarehouseId,
            WarehouseId newDestinationWarehouseId,
            List<WarehouseTransferLine> newLines,
            long expectedRevision) {
        if (expectedRevision != payloadRevision) {
            throw new TransferDocumentOptimisticLockException(
                    documentId, expectedRevision, payloadRevision);
        }
        return of(
                documentId,
                newSourceWarehouseId,
                newDestinationWarehouseId,
                payloadSchemaVersion,
                payloadRevision + 1,
                newLines);
    }

    private static void validateLines(List<WarehouseTransferLine> lines) {
        Set<UUID> materials = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (WarehouseTransferLine line : lines) {
            Objects.requireNonNull(line, "line");
            if (!materials.add(line.materialReferenceId().value())) {
                throw new IllegalArgumentException(
                        "Duplicate material in transfer document: "
                                + line.materialReferenceId().value());
            }
            if (!orders.add(line.lineOrder())) {
                throw new IllegalArgumentException(
                        "Duplicate lineOrder in transfer document: " + line.lineOrder());
            }
        }
    }

    /** Returns lines sorted by {@code lineOrder}. */
    public List<WarehouseTransferLine> orderedLines() {
        List<WarehouseTransferLine> sorted = new ArrayList<>(lines);
        sorted.sort((a, b) -> Integer.compare(a.lineOrder(), b.lineOrder()));
        return List.copyOf(sorted);
    }

    public UUID documentId() {
        return documentId;
    }

    public WarehouseId sourceWarehouseId() {
        return sourceWarehouseId;
    }

    public WarehouseId destinationWarehouseId() {
        return destinationWarehouseId;
    }

    public int payloadSchemaVersion() {
        return payloadSchemaVersion;
    }

    public long payloadRevision() {
        return payloadRevision;
    }

    public List<WarehouseTransferLine> lines() {
        return lines;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseTransferDocument that)) {
            return false;
        }
        return documentId.equals(that.documentId);
    }

    @Override
    public int hashCode() {
        return documentId.hashCode();
    }
}
