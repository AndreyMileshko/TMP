package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.WarehouseTransferLine;
import com.tmp.warehouse.domain.WarehouseTransferLineId;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Shared structural validation for Transfer Document physical send allocations (Stage 3.5.6 /
 * 3.5.7).
 *
 * <p>{@link #requireCompleteCoverage} remains the POST / processor invariant (exact coverage of
 * the payload being posted). {@link #analyzeSendSupply} validates Stage 3.5.7 shortfall rules
 * against the pre-transform DRAFT payload (0 ≤ sent ≤ requested; reject all-zero and over-supply).
 */
public final class TransferDocumentSendAllocationValidator {

    private TransferDocumentSendAllocationValidator() {}

    /**
     * Exact coverage of every payload line — used by {@code onPost} and by full-send after any
     * shortfall payload shrink.
     */
    public static void requireCompleteCoverage(
            WarehouseTransferDocument payload, List<AllocationInput> allocations) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(allocations, "allocations");
        List<WarehouseTransferLine> lines = payload.orderedLines();
        if (lines.isEmpty()) {
            throw new InvalidWarehouseStateException(
                    "Transfer document send requires at least one line: documentId="
                            + payload.documentId());
        }
        if (allocations.isEmpty()) {
            throw new InvalidWarehouseStateException(
                    "Transfer document send requires source allocations: documentId="
                            + payload.documentId());
        }

        Map<UUID, WarehouseTransferLine> linesById = new HashMap<>();
        for (WarehouseTransferLine line : lines) {
            linesById.put(line.id().value(), line);
        }

        Set<String> uniqueKeys = new HashSet<>();
        Map<UUID, BigDecimal> totals = new HashMap<>();
        Set<UUID> coveredLines = new HashSet<>();

        for (AllocationInput allocation : allocations) {
            Objects.requireNonNull(allocation, "allocation");
            Objects.requireNonNull(allocation.lineId(), "lineId");
            Objects.requireNonNull(allocation.sourceStorageCellId(), "sourceStorageCellId");
            Objects.requireNonNull(allocation.quantity(), "quantity");
            if (allocation.quantity().signum() <= 0) {
                throw new InvalidWarehouseStateException(
                        "Send allocation quantity must be positive: " + allocation.quantity());
            }
            WarehouseTransferLine line = linesById.get(allocation.lineId());
            if (line == null) {
                throw new InvalidWarehouseStateException(
                        "Send allocation references unknown line for document: documentId="
                                + payload.documentId()
                                + ", lineId="
                                + allocation.lineId());
            }
            String key = allocation.lineId() + "|" + allocation.sourceStorageCellId();
            if (!uniqueKeys.add(key)) {
                throw new InvalidWarehouseStateException(
                        "Duplicate send allocation for line "
                                + allocation.lineId()
                                + " cell="
                                + allocation.sourceStorageCellId());
            }
            coveredLines.add(allocation.lineId());
            totals.merge(allocation.lineId(), allocation.quantity(), BigDecimal::add);
        }

        for (WarehouseTransferLine line : lines) {
            UUID lineId = line.id().value();
            if (!coveredLines.contains(lineId)) {
                throw new InvalidWarehouseStateException(
                        "Send allocations missing coverage for line: " + lineId);
            }
            BigDecimal total = totals.get(lineId);
            if (total.compareTo(line.quantity().value()) != 0) {
                throw new InvalidWarehouseStateException(
                        "Send allocation total must equal line quantity exactly: lineId="
                                + lineId
                                + ", allocations="
                                + total
                                + ", lineQuantity="
                                + line.quantity().value());
            }
        }

        if (coveredLines.size() != linesById.size()) {
            throw new InvalidWarehouseStateException(
                    "Send allocations must cover exactly the document lines: documentId="
                            + payload.documentId());
        }
    }

    /**
     * Stage 3.5.7 pre-POST supply analysis against the current DRAFT payload. Allows under-supply
     * (shortfall); rejects over-supply and all-zero / empty selection.
     */
    public static SendSupplyAnalysis analyzeSendSupply(
            WarehouseTransferDocument payload, List<AllocationInput> allocations) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(allocations, "allocations");
        List<WarehouseTransferLine> lines = payload.orderedLines();
        if (lines.isEmpty()) {
            throw new InvalidWarehouseStateException(
                    "Transfer document send requires at least one line: documentId="
                            + payload.documentId());
        }

        Map<UUID, WarehouseTransferLine> linesById = new HashMap<>();
        for (WarehouseTransferLine line : lines) {
            linesById.put(line.id().value(), line);
        }

        Set<String> uniqueKeys = new HashSet<>();
        Map<UUID, BigDecimal> sentByLine = new LinkedHashMap<>();
        for (WarehouseTransferLine line : lines) {
            sentByLine.put(line.id().value(), BigDecimal.ZERO);
        }

        for (AllocationInput allocation : allocations) {
            Objects.requireNonNull(allocation, "allocation");
            Objects.requireNonNull(allocation.lineId(), "lineId");
            Objects.requireNonNull(allocation.sourceStorageCellId(), "sourceStorageCellId");
            Objects.requireNonNull(allocation.quantity(), "quantity");
            if (allocation.quantity().signum() <= 0) {
                throw new InvalidWarehouseStateException(
                        "Send allocation quantity must be positive: " + allocation.quantity());
            }
            WarehouseTransferLine line = linesById.get(allocation.lineId());
            if (line == null) {
                throw new InvalidWarehouseStateException(
                        "Send allocation references unknown line for document: documentId="
                                + payload.documentId()
                                + ", lineId="
                                + allocation.lineId());
            }
            String key = allocation.lineId() + "|" + allocation.sourceStorageCellId();
            if (!uniqueKeys.add(key)) {
                throw new InvalidWarehouseStateException(
                        "Duplicate send allocation for line "
                                + allocation.lineId()
                                + " cell="
                                + allocation.sourceStorageCellId());
            }
            sentByLine.merge(allocation.lineId(), allocation.quantity(), BigDecimal::add);
        }

        BigDecimal totalSent = BigDecimal.ZERO;
        boolean hasRemainder = false;
        Map<UUID, BigDecimal> remainderByLine = new LinkedHashMap<>();
        for (WarehouseTransferLine line : lines) {
            UUID lineId = line.id().value();
            BigDecimal requested = line.quantity().value();
            BigDecimal sent = sentByLine.getOrDefault(lineId, BigDecimal.ZERO);
            if (sent.compareTo(requested) > 0) {
                throw new InvalidWarehouseStateException(
                        "Send allocation total must not exceed line quantity: lineId="
                                + lineId
                                + ", allocations="
                                + sent
                                + ", lineQuantity="
                                + requested);
            }
            BigDecimal remaining = requested.subtract(sent);
            remainderByLine.put(lineId, remaining);
            totalSent = totalSent.add(sent);
            if (remaining.signum() > 0) {
                hasRemainder = true;
            }
        }

        if (totalSent.signum() <= 0) {
            throw new InvalidWarehouseStateException(
                    "Transfer document send requires at least one positive source allocation"
                            + " (nothing selected / nothing supplied): documentId="
                            + payload.documentId());
        }

        boolean fullSend = !hasRemainder;
        return new SendSupplyAnalysis(
                Map.copyOf(sentByLine), Map.copyOf(remainderByLine), totalSent, fullSend);
    }

    public static void requireSourceCellsBelongToSourceWarehouse(
            WarehouseId sourceWarehouseId,
            List<AllocationInput> allocations,
            WarehouseCatalogRepository catalog) {
        Objects.requireNonNull(sourceWarehouseId, "sourceWarehouseId");
        Objects.requireNonNull(allocations, "allocations");
        Objects.requireNonNull(catalog, "catalog");
        Map<UUID, StorageCell> cellsById = new HashMap<>();
        for (StorageCell cell : catalog.findStorageCellsByWarehouse(sourceWarehouseId)) {
            cellsById.put(cell.id().value(), cell);
        }
        for (AllocationInput allocation : allocations) {
            StorageCell cell = cellsById.get(allocation.sourceStorageCellId());
            if (cell == null) {
                throw new InvalidWarehouseStateException(
                        "Source storage cell does not belong to document source warehouse: cellId="
                                + allocation.sourceStorageCellId()
                                + ", warehouseId="
                                + sourceWarehouseId);
            }
            if (!cell.active()) {
                throw new InvalidWarehouseStateException(
                        "Source storage cell is inactive: cellId="
                                + allocation.sourceStorageCellId());
            }
        }
    }

    public record AllocationInput(UUID lineId, UUID sourceStorageCellId, BigDecimal quantity) {

        public static AllocationInput of(
                WarehouseTransferLineId lineId,
                StorageCellId sourceStorageCellId,
                BigDecimal quantity) {
            return new AllocationInput(
                    lineId.value(), sourceStorageCellId.value(), quantity);
        }
    }

    /** Result of Stage 3.5.7 DRAFT supply analysis before optional payload shrink. */
    public record SendSupplyAnalysis(
            Map<UUID, BigDecimal> sentByLine,
            Map<UUID, BigDecimal> remainderByLine,
            BigDecimal totalSent,
            boolean fullSend) {
        public SendSupplyAnalysis {
            Objects.requireNonNull(sentByLine, "sentByLine");
            Objects.requireNonNull(remainderByLine, "remainderByLine");
            Objects.requireNonNull(totalSent, "totalSent");
            sentByLine = Map.copyOf(sentByLine);
            remainderByLine = Map.copyOf(remainderByLine);
        }
    }
}
