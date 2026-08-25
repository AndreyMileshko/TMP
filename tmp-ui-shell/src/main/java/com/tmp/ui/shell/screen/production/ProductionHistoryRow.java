package com.tmp.ui.shell.screen.production;

import java.util.Objects;
import java.util.UUID;

/** Read-only presentation row for production history. */
public final class ProductionHistoryRow {

    private final UUID entryId;
    private final String occurredAt;
    private final String typeLabel;
    private final String actor;
    private final String summary;

    public ProductionHistoryRow(
            UUID entryId, String occurredAt, String typeLabel, String actor, String summary) {
        this.entryId = Objects.requireNonNull(entryId, "entryId");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.typeLabel = Objects.requireNonNull(typeLabel, "typeLabel");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.summary = Objects.requireNonNull(summary, "summary");
    }

    public UUID entryId() {
        return entryId;
    }

    public String occurredAt() {
        return occurredAt;
    }

    public String typeLabel() {
        return typeLabel;
    }

    public String actor() {
        return actor;
    }

    public String summary() {
        return summary;
    }
}
