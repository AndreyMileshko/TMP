package com.tmp.production.testsupport;

import com.tmp.production.domain.ProductionHistoryEntry;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.repository.ProductionHistoryRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory append-only history store for unit tests. */
public final class InMemoryProductionHistoryRepository implements ProductionHistoryRepository {

    private final List<ProductionHistoryEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public ProductionHistoryEntry append(ProductionHistoryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        entries.add(entry);
        return entry;
    }

    @Override
    public List<ProductionHistoryEntry> listByOrder(SourceOrderId sourceOrderId) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        return entries.stream()
                .filter(entry -> entry.sourceOrderId().equals(sourceOrderId))
                .sorted(
                        Comparator.comparing(ProductionHistoryEntry::occurredAt)
                                .thenComparing(ProductionHistoryEntry::recordedAt)
                                .thenComparing(entry -> entry.entryId().value()))
                .toList();
    }

    public List<ProductionHistoryEntry> all() {
        return List.copyOf(entries);
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    public List<ProductionHistoryEntry> ofType(
            com.tmp.production.domain.ProductionHistoryEntry.ProductionHistoryType type) {
        List<ProductionHistoryEntry> matched = new ArrayList<>();
        for (ProductionHistoryEntry entry : entries) {
            if (entry.historyType() == type) {
                matched.add(entry);
            }
        }
        return List.copyOf(matched);
    }
}
