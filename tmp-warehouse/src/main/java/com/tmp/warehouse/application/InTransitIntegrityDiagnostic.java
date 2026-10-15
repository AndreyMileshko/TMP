package com.tmp.warehouse.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-only comparison of positive IN_TRANSIT stock against document-linked outstanding and
 * unreceived legacy transfer-operation contexts.
 *
 * <p>Does not mutate stock, documents, or operations. Intended for verification/tests and the SQL
 * script scripts/warehouse-in-transit-integrity-diagnostic.sql.
 */
public final class InTransitIntegrityDiagnostic {

    private InTransitIntegrityDiagnostic() {}

    public record StockKey(UUID warehouseId, UUID storageCellId, UUID materialReferenceId) {
        public StockKey {
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(storageCellId, "storageCellId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        }
    }

    public record RecordedInTransit(
            StockKey key, BigDecimal quantity, UUID stockPositionId, String unitOfMeasure) {
        public RecordedInTransit {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(stockPositionId, "stockPositionId");
            unitOfMeasure = unitOfMeasure == null ? "" : unitOfMeasure;
        }
    }

    public record ExplainedOutstanding(StockKey key, BigDecimal quantity) {
        public ExplainedOutstanding {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(quantity, "quantity");
        }
    }

    public record OrphanBalance(
            StockKey key,
            BigDecimal recorded,
            BigDecimal documentOutstanding,
            BigDecimal legacyContextOutstanding,
            BigDecimal orphanQuantity,
            UUID stockPositionId,
            String unitOfMeasure) {}

    public record Report(
            List<RecordedInTransit> recordedPositions,
            List<ExplainedOutstanding> documentOutstanding,
            List<ExplainedOutstanding> legacyContextOutstanding,
            List<OrphanBalance> orphans) {
        public Report {
            recordedPositions = List.copyOf(recordedPositions);
            documentOutstanding = List.copyOf(documentOutstanding);
            legacyContextOutstanding = List.copyOf(legacyContextOutstanding);
            orphans = List.copyOf(orphans);
        }

        public boolean hasOrphans() {
            return !orphans.isEmpty();
        }
    }

    public static Report analyze(
            List<RecordedInTransit> recorded,
            List<ExplainedOutstanding> documentOutstanding,
            List<ExplainedOutstanding> legacyContextOutstanding) {
        Objects.requireNonNull(recorded, "recorded");
        Objects.requireNonNull(documentOutstanding, "documentOutstanding");
        Objects.requireNonNull(legacyContextOutstanding, "legacyContextOutstanding");

        Map<StockKey, BigDecimal> docByKey = sumByKey(documentOutstanding);
        Map<StockKey, BigDecimal> legacyByKey = sumByKey(legacyContextOutstanding);
        List<OrphanBalance> orphans = new ArrayList<>();
        for (RecordedInTransit row : recorded) {
            if (row.quantity().signum() <= 0) {
                continue;
            }
            BigDecimal doc = docByKey.getOrDefault(row.key(), BigDecimal.ZERO);
            BigDecimal legacy = legacyByKey.getOrDefault(row.key(), BigDecimal.ZERO);
            BigDecimal orphan = row.quantity().subtract(doc).subtract(legacy);
            if (orphan.compareTo(BigDecimal.ZERO) != 0) {
                orphans.add(
                        new OrphanBalance(
                                row.key(),
                                row.quantity(),
                                doc,
                                legacy,
                                orphan,
                                row.stockPositionId(),
                                row.unitOfMeasure()));
            }
        }
        return new Report(recorded, documentOutstanding, legacyContextOutstanding, orphans);
    }

    private static Map<StockKey, BigDecimal> sumByKey(List<ExplainedOutstanding> rows) {
        Map<StockKey, BigDecimal> map = new HashMap<>();
        for (ExplainedOutstanding row : rows) {
            if (row.quantity().signum() <= 0) {
                continue;
            }
            map.merge(row.key(), row.quantity(), BigDecimal::add);
        }
        return map;
    }
}