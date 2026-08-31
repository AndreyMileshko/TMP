package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionApplicationApi.WarehouseTransferRefView;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Read-only presentation row for a logical material transfer. */
public final class LogicalTransferRow {

    private final UUID id;
    private final UUID templateId;
    private final String createdAtLabel;
    private final String lifecycleLabel;
    private final List<WarehouseTransferRefView> warehouseOperations;

    public LogicalTransferRow(
            UUID id,
            UUID templateId,
            String createdAtLabel,
            String lifecycleLabel,
            List<WarehouseTransferRefView> warehouseOperations) {
        this.id = Objects.requireNonNull(id, "id");
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.createdAtLabel = Objects.requireNonNull(createdAtLabel, "createdAtLabel");
        this.lifecycleLabel = Objects.requireNonNull(lifecycleLabel, "lifecycleLabel");
        this.warehouseOperations =
                List.copyOf(Objects.requireNonNull(warehouseOperations, "warehouseOperations"));
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

    public String lifecycleLabel() {
        return lifecycleLabel;
    }

    public List<WarehouseTransferRefView> warehouseOperations() {
        return warehouseOperations;
    }

    @Override
    public String toString() {
        return createdAtLabel + " [" + lifecycleLabel + "]";
    }
}
