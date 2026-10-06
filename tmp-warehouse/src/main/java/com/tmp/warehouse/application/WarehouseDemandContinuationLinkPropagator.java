package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.WarehouseDemandTransferLink;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.WarehouseTransferLine;
import com.tmp.warehouse.domain.repository.WarehouseDemandRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Propagates Demand ↔ Transfer lineage onto SHORTFALL / RECEIVE_SHORTFALL continuation lines
 * (B3B-3A). Does not change Transfer shortfall semantics. Legacy Transfers without Demand links are
 * left unchanged.
 *
 * <p>Must run inside the same Warehouse transaction that creates the continuation.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators.")
public final class WarehouseDemandContinuationLinkPropagator {

    private final WarehouseDemandRepository demands;

    public WarehouseDemandContinuationLinkPropagator(WarehouseDemandRepository demands) {
        this.demands = Objects.requireNonNull(demands, "demands");
    }

    /**
     * For each parent Transfer line that has a Demand link, finds the matching continuation
     * remainder line (same materialReferenceId + lineOrder) and inserts a DemandTransferLink when
     * absent.
     *
     * @return number of newly inserted links
     */
    public int propagate(
            List<WarehouseTransferLine> parentLines, WarehouseTransferDocument continuation) {
        Objects.requireNonNull(parentLines, "parentLines");
        Objects.requireNonNull(continuation, "continuation");
        Map<String, WarehouseTransferLine> continuationByKey = indexByMaterialAndOrder(continuation);
        int inserted = 0;
        for (WarehouseTransferLine parentLine : parentLines) {
            Objects.requireNonNull(parentLine, "parentLine");
            Optional<WarehouseDemandTransferLink> parentLink =
                    demands.findTransferLinkByTransferLineId(parentLine.id());
            if (parentLink.isEmpty()) {
                continue;
            }
            WarehouseTransferLine continuationLine =
                    continuationByKey.get(key(parentLine));
            if (continuationLine == null) {
                continue;
            }
            boolean created =
                    demands.insertTransferLinkIfAbsent(
                            WarehouseDemandTransferLink.create(
                                    parentLink.get().demandLineId(),
                                    continuation.documentId(),
                                    continuationLine.id(),
                                    continuationLine.quantity()));
            if (created) {
                inserted++;
            }
        }
        return inserted;
    }

    private static Map<String, WarehouseTransferLine> indexByMaterialAndOrder(
            WarehouseTransferDocument continuation) {
        Map<String, WarehouseTransferLine> byKey = new HashMap<>();
        for (WarehouseTransferLine line : continuation.orderedLines()) {
            byKey.put(key(line), line);
        }
        return byKey;
    }

    private static String key(WarehouseTransferLine line) {
        return line.materialReferenceId().value() + "|" + line.lineOrder();
    }
}
