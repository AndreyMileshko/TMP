package com.tmp.ui.shell.screen.production;

import java.util.Objects;
import java.util.UUID;

/** Read-only presentation row for a logical material transfer. */
public final class LogicalTransferRow {

    private final UUID id;
    private final UUID templateId;
    private final String createdAtLabel;

    public LogicalTransferRow(UUID id, UUID templateId, String createdAtLabel) {
        this.id = Objects.requireNonNull(id, "id");
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.createdAtLabel = Objects.requireNonNull(createdAtLabel, "createdAtLabel");
    }

    public UUID id() {
        return id;
    }

    public UUID templateId() {
        return templateId;
    }

    public String createdAtLabel() {
        return createdAtLabel;
    }

    @Override
    public String toString() {
        return createdAtLabel + " (" + id + ")";
    }
}
