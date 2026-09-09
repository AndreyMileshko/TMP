package com.tmp.warehouse.application;

import com.tmp.warehouse.api.DemandSourceUnavailableException;
import com.tmp.warehouse.api.DemandSourceUnavailableException.UnavailableDemand;
import com.tmp.warehouse.api.WarehouseApi.MaterialDemand;
import com.tmp.warehouse.api.WarehouseApi.MaterialSourceRoutingOutcome;
import com.tmp.warehouse.api.WarehouseApi.MaterialSourceRoutingResult;
import com.tmp.warehouse.api.WarehouseDemandCommandApi;
import com.tmp.warehouse.application.WarehouseTransferDocumentService.CreatedTransferDocument;
import com.tmp.warehouse.application.WarehouseTransferDocumentService.LineInput;
import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default {@link WarehouseDemandCommandApi} implementation (Stage 3.5.10).
 *
 * <p>Single trusted demand-driven creation path without the responsibility guard. Reuses {@link
 * MaterialSourceRoutingService} for source selection and {@link WarehouseTransferDocumentService}
 * for DRAFT creation. Route-all → validate-all → group → create. Created document line quantities
 * equal the FULL requested demand (never routed quantity).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators injected by the container.")
public final class DefaultWarehouseDemandCommandApi implements WarehouseDemandCommandApi {

    private final MaterialSourceRoutingService sourceRouting;
    private final WarehouseTransferDocumentService transferDocuments;
    private final WarehouseCatalogRepository warehouses;
    private final MaterialReferenceRepository materials;
    private final TransactionTemplate transactionTemplate;

    public DefaultWarehouseDemandCommandApi(
            MaterialSourceRoutingService sourceRouting,
            WarehouseTransferDocumentService transferDocuments,
            WarehouseCatalogRepository warehouses,
            MaterialReferenceRepository materials,
            TransactionTemplate transactionTemplate) {
        this.sourceRouting = Objects.requireNonNull(sourceRouting, "sourceRouting");
        this.transferDocuments = Objects.requireNonNull(transferDocuments, "transferDocuments");
        this.warehouses = Objects.requireNonNull(warehouses, "warehouses");
        this.materials = Objects.requireNonNull(materials, "materials");
        this.transactionTemplate =
                Objects.requireNonNull(transactionTemplate, "transactionTemplate");
    }

    @Override
    public RoutedTransferResult createRoutedTransferDocuments(CreateRoutedTransferCommand command) {
        Objects.requireNonNull(command, "command");
        RoutedTransferResult result =
                transactionTemplate.execute(status -> route(command));
        if (result == null) {
            throw new IllegalStateException("Routed transfer creation returned null");
        }
        return result;
    }

    private RoutedTransferResult route(CreateRoutedTransferCommand command) {
        UUID destinationWarehouseId = command.destinationWarehouseId();
        requireActiveWarehouse(destinationWarehouseId);

        List<DemandLine> demandLines = command.lines();
        if (demandLines.isEmpty()) {
            throw new InvalidWarehouseStateException(
                    "Routed transfer requires at least one demand line");
        }
        for (DemandLine line : demandLines) {
            if (materials.findById(MaterialReferenceId.of(line.materialReferenceId())).isEmpty()) {
                throw new IllegalArgumentException(
                        "Material reference not found: " + line.materialReferenceId());
            }
        }

        // Route ALL lines first (Stage 3.5.10 §10). demandKey correlates results in input order.
        List<MaterialDemand> demands = new ArrayList<>(demandLines.size());
        for (DemandLine line : demandLines) {
            demands.add(
                    new MaterialDemand(
                            line.demandKey(), line.materialReferenceId(), line.quantity()));
        }
        List<MaterialSourceRoutingResult> routing =
                sourceRouting.routeMaterials(destinationWarehouseId, demands);

        // Validate ALL: every line must have a selected source (§9).
        List<UnavailableDemand> unavailable = new ArrayList<>();
        for (MaterialSourceRoutingResult r : routing) {
            if (r.outcome() != MaterialSourceRoutingOutcome.SOURCE_SELECTED) {
                unavailable.add(new UnavailableDemand(r.demandKey(), r.materialReferenceId()));
            }
        }
        if (!unavailable.isEmpty()) {
            throw new DemandSourceUnavailableException(unavailable);
        }

        // Correlate routing back to demand lines by demandKey (preserving input order).
        Map<String, DemandLine> demandByKey = new LinkedHashMap<>();
        for (DemandLine line : demandLines) {
            if (demandByKey.put(line.demandKey(), line) != null) {
                throw new InvalidWarehouseStateException(
                        "Duplicate demandKey in routed transfer command: " + line.demandKey());
            }
        }

        // Group by selected source warehouse, preserving original demand order (§11).
        Map<UUID, List<PlannedLine>> bySource = new LinkedHashMap<>();
        for (MaterialSourceRoutingResult r : routing) {
            DemandLine demand = demandByKey.get(r.demandKey());
            PlannedLine planned = new PlannedLine(demand, r, UUID.randomUUID());
            bySource.computeIfAbsent(r.sourceWarehouseId(), ignored -> new ArrayList<>())
                    .add(planned);
        }

        List<GeneratedDocument> documents = new ArrayList<>();
        Map<String, UUID> documentIdByKey = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<PlannedLine>> group : bySource.entrySet()) {
            UUID sourceWarehouseId = group.getKey();
            List<PlannedLine> plannedLines = group.getValue();
            List<LineInput> lineInputs = new ArrayList<>(plannedLines.size());
            int order = 1;
            for (PlannedLine planned : plannedLines) {
                // Document line quantity = FULL requested demand quantity, NOT routed (§8).
                lineInputs.add(
                        new LineInput(
                                planned.transferLineId(),
                                planned.demand().materialReferenceId(),
                                planned.demand().quantity(),
                                order++));
            }
            CreatedTransferDocument created =
                    transferDocuments.createDemandDraft(
                            WarehouseId.of(sourceWarehouseId),
                            WarehouseId.of(destinationWarehouseId),
                            lineInputs);
            UUID documentId = created.metadata().id();
            documents.add(
                    new GeneratedDocument(documentId, sourceWarehouseId, destinationWarehouseId));
            for (PlannedLine planned : plannedLines) {
                documentIdByKey.put(planned.demand().demandKey(), documentId);
            }
        }

        // Build routing snapshot in the original demand-line order.
        List<RoutedLine> routedLines = new ArrayList<>(routing.size());
        for (MaterialSourceRoutingResult r : routing) {
            UUID documentId = documentIdByKey.get(r.demandKey());
            UUID transferLineId =
                    bySource.values().stream()
                            .flatMap(List::stream)
                            .filter(p -> p.demand().demandKey().equals(r.demandKey()))
                            .map(PlannedLine::transferLineId)
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Missing transfer line for demandKey: "
                                                            + r.demandKey()));
            routedLines.add(
                    new RoutedLine(
                            r.demandKey(),
                            r.materialReferenceId(),
                            r.sourceWarehouseId(),
                            r.sourceWarehouseCode(),
                            r.availableAtSelectedSource(),
                            r.routedQuantity(),
                            r.uncoveredQuantity(),
                            documentId,
                            transferLineId));
        }

        return new RoutedTransferResult(documents, routedLines);
    }

    private void requireActiveWarehouse(UUID warehouseId) {
        Warehouse warehouse =
                warehouses.findAll().stream()
                        .filter(w -> w.id().value().equals(warehouseId))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Warehouse not found: " + warehouseId));
        if (!warehouse.active()) {
            throw new InvalidWarehouseStateException(
                    "Destination warehouse is inactive: " + warehouseId);
        }
    }

    private record PlannedLine(
            DemandLine demand, MaterialSourceRoutingResult routing, UUID transferLineId) {}
}
