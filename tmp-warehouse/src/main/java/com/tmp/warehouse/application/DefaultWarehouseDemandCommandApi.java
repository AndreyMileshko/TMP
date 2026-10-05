package com.tmp.warehouse.application;

import com.tmp.warehouse.api.WarehouseApi.MaterialDemand;
import com.tmp.warehouse.api.WarehouseApi.MaterialSourceRoutingOutcome;
import com.tmp.warehouse.api.WarehouseApi.MaterialSourceRoutingResult;
import com.tmp.warehouse.api.WarehouseDemandCommandApi;
import com.tmp.warehouse.api.WarehouseDemandPayloadConflictException;
import com.tmp.warehouse.application.WarehouseTransferDocumentService.CreatedTransferDocument;
import com.tmp.warehouse.application.WarehouseTransferDocumentService.LineInput;
import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseDemand;
import com.tmp.warehouse.domain.WarehouseDemandLine;
import com.tmp.warehouse.domain.WarehouseDemandTransferLink;
import com.tmp.warehouse.domain.WarehouseDemandWaitingReason;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseTransferLineId;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import com.tmp.warehouse.domain.repository.WarehouseDemandRepository;
import com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default {@link WarehouseDemandCommandApi}: accept Production Demand, resolve materials, and
 * best-effort create Transfer DRAFTs for uniquely resolved lines with positive AVAILABLE stock.
 *
 * <p>Business no-route outcomes persist WAITING reasons and do not throw. Technical failures
 * propagate and roll back with the caller's outer transaction.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators injected by the container.")
public final class DefaultWarehouseDemandCommandApi implements WarehouseDemandCommandApi {

    private final MaterialSourceRoutingService sourceRouting;
    private final WarehouseTransferDocumentService transferDocuments;
    private final WarehouseCatalogRepository warehouses;
    private final MaterialReferenceRepository materials;
    private final WarehouseDemandRepository demands;
    private final WarehouseTransferDocumentRepository transferDocumentRepository;
    private final WarehouseMaterialReferenceResolver materialResolver;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DefaultWarehouseDemandCommandApi(
            MaterialSourceRoutingService sourceRouting,
            WarehouseTransferDocumentService transferDocuments,
            WarehouseCatalogRepository warehouses,
            MaterialReferenceRepository materials,
            WarehouseDemandRepository demands,
            WarehouseTransferDocumentRepository transferDocumentRepository,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.sourceRouting = Objects.requireNonNull(sourceRouting, "sourceRouting");
        this.transferDocuments = Objects.requireNonNull(transferDocuments, "transferDocuments");
        this.warehouses = Objects.requireNonNull(warehouses, "warehouses");
        this.materials = Objects.requireNonNull(materials, "materials");
        this.demands = Objects.requireNonNull(demands, "demands");
        this.transferDocumentRepository =
                Objects.requireNonNull(transferDocumentRepository, "transferDocumentRepository");
        this.materialResolver = new WarehouseMaterialReferenceResolver();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transactionTemplate =
                Objects.requireNonNull(transactionTemplate, "transactionTemplate");
    }

    @Override
    public AcceptProductionDemandResult acceptProductionDemand(
            AcceptProductionDemandCommand command) {
        Objects.requireNonNull(command, "command");
        AcceptProductionDemandResult result =
                transactionTemplate.execute(status -> accept(command));
        if (result == null) {
            throw new IllegalStateException("acceptProductionDemand returned null");
        }
        return result;
    }

    private AcceptProductionDemandResult accept(AcceptProductionDemandCommand command) {
        requireActiveWarehouse(command.destinationWarehouseId());
        if (command.lines().isEmpty()) {
            throw new InvalidWarehouseStateException(
                    "Production Demand acceptance requires at least one line");
        }

        Optional<WarehouseDemand> existing =
                demands.findBySourceMaterialRequirementId(command.sourceMaterialRequirementId());
        if (existing.isPresent()) {
            assertPayloadMatches(existing.get(), command);
            return toResult(existing.get(), false);
        }

        List<MaterialReference> catalog = materials.findAll();
        List<PreparedLine> prepared = new ArrayList<>(command.lines().size());
        for (ProductionDemandLine line : command.lines()) {
            prepared.add(prepareLine(line, catalog));
        }

        List<PreparedLine> routable =
                prepared.stream()
                        .filter(p -> p.waitingReason() == null && p.materialReferenceId() != null)
                        .toList();
        Map<String, MaterialSourceRoutingResult> routingBySourceLine = new LinkedHashMap<>();
        if (!routable.isEmpty()) {
            List<MaterialDemand> routingDemands = new ArrayList<>(routable.size());
            for (PreparedLine line : routable) {
                routingDemands.add(
                        new MaterialDemand(
                                line.sourceLineId().toString(),
                                line.materialReferenceId().value(),
                                line.quantity()));
            }
            List<MaterialSourceRoutingResult> routing =
                    sourceRouting.routeMaterials(command.destinationWarehouseId(), routingDemands);
            for (MaterialSourceRoutingResult r : routing) {
                routingBySourceLine.put(r.demandKey(), r);
            }
            for (int i = 0; i < prepared.size(); i++) {
                PreparedLine line = prepared.get(i);
                if (line.waitingReason() != null || line.materialReferenceId() == null) {
                    continue;
                }
                MaterialSourceRoutingResult r =
                        routingBySourceLine.get(line.sourceLineId().toString());
                if (r == null
                        || r.outcome() != MaterialSourceRoutingOutcome.SOURCE_SELECTED) {
                    prepared.set(
                            i,
                            line.withWaiting(WarehouseDemandWaitingReason.NO_AVAILABLE_STOCK));
                }
            }
        }

        Instant acceptedAt = clock.instant();
        List<WarehouseDemandLine> demandLines = new ArrayList<>(prepared.size());
        Map<UUID, PreparedLine> preparedBySourceLine = new LinkedHashMap<>();
        for (PreparedLine line : prepared) {
            WarehouseDemandLine demandLine =
                    WarehouseDemandLine.create(
                            line.sourceLineId(),
                            line.materialCode(),
                            line.materialName(),
                            line.color(),
                            line.unitOfMeasure(),
                            line.lengthMm(),
                            StockQuantity.of(line.quantity()),
                            line.materialReferenceId(),
                            line.waitingReason());
            demandLines.add(demandLine);
            preparedBySourceLine.put(line.sourceLineId(), line.withDemandLineId(demandLine.id()));
        }

        WarehouseDemand demand =
                WarehouseDemand.accept(
                        command.sourceMaterialRequirementId(),
                        WarehouseId.of(command.destinationWarehouseId()),
                        acceptedAt,
                        command.acceptedBy(),
                        demandLines);
        demands.insert(demand);

        Map<UUID, List<PlannedTransferLine>> bySource = new LinkedHashMap<>();
        for (WarehouseDemandLine demandLine : demand.lines()) {
            PreparedLine preparedLine =
                    preparedBySourceLine.get(demandLine.sourceMaterialRequirementLineId());
            if (preparedLine.waitingReason() != null) {
                continue;
            }
            MaterialSourceRoutingResult routing =
                    routingBySourceLine.get(
                            demandLine.sourceMaterialRequirementLineId().toString());
            if (routing == null
                    || routing.outcome() != MaterialSourceRoutingOutcome.SOURCE_SELECTED) {
                continue;
            }
            UUID transferLineId = UUID.randomUUID();
            PlannedTransferLine planned =
                    new PlannedTransferLine(
                            demandLine,
                            routing,
                            transferLineId,
                            demandLine.requiredQuantity().value());
            bySource
                    .computeIfAbsent(routing.sourceWarehouseId(), ignored -> new ArrayList<>())
                    .add(planned);
        }

        List<GeneratedDocument> documents = new ArrayList<>();
        Map<UUID, PlannedTransferLine> plannedByDemandLineId = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<PlannedTransferLine>> group : bySource.entrySet()) {
            UUID sourceWarehouseId = group.getKey();
            List<PlannedTransferLine> plannedLines = group.getValue();
            List<LineInput> lineInputs = new ArrayList<>(plannedLines.size());
            int order = 1;
            for (PlannedTransferLine planned : plannedLines) {
                lineInputs.add(
                        new LineInput(
                                planned.transferLineId(),
                                planned.demandLine().materialReferenceId().orElseThrow().value(),
                                planned.linkedQuantity(),
                                order++));
            }
            CreatedTransferDocument created =
                    transferDocuments.createDemandDraft(
                            WarehouseId.of(sourceWarehouseId),
                            WarehouseId.of(command.destinationWarehouseId()),
                            lineInputs);
            UUID documentId = created.metadata().id();
            documents.add(
                    new GeneratedDocument(
                            documentId, sourceWarehouseId, command.destinationWarehouseId()));
            for (PlannedTransferLine planned : plannedLines) {
                demands.insertTransferLink(
                        WarehouseDemandTransferLink.create(
                                planned.demandLine().id(),
                                documentId,
                                WarehouseTransferLineId.of(planned.transferLineId()),
                                StockQuantity.of(planned.linkedQuantity())));
                plannedByDemandLineId.put(
                        planned.demandLine().id().value(), planned.withDocument(documentId));
            }
        }

        List<DemandLineOutcome> outcomes = new ArrayList<>(demand.lines().size());
        for (WarehouseDemandLine demandLine : demand.lines()) {
            outcomes.add(toOutcome(demandLine, plannedByDemandLineId.get(demandLine.id().value())));
        }
        return new AcceptProductionDemandResult(
                demand.id().value(), true, outcomes, documents);
    }

    private PreparedLine prepareLine(ProductionDemandLine line, List<MaterialReference> catalog) {
        WarehouseMaterialReferenceResolver.Result resolution =
                materialResolver.resolve(
                        line.materialCode(), line.color(), line.unitOfMeasure(), catalog);
        return switch (resolution.status()) {
            case UNMATCHED ->
                    new PreparedLine(
                            line.sourceMaterialRequirementLineId(),
                            line.materialCode(),
                            line.materialName(),
                            line.color(),
                            line.unitOfMeasure(),
                            line.lengthMm(),
                            line.quantity(),
                            null,
                            WarehouseDemandWaitingReason.MATERIAL_UNMATCHED,
                            null);
            case AMBIGUOUS ->
                    new PreparedLine(
                            line.sourceMaterialRequirementLineId(),
                            line.materialCode(),
                            line.materialName(),
                            line.color(),
                            line.unitOfMeasure(),
                            line.lengthMm(),
                            line.quantity(),
                            null,
                            WarehouseDemandWaitingReason.MATERIAL_AMBIGUOUS,
                            null);
            case RESOLVED ->
                    new PreparedLine(
                            line.sourceMaterialRequirementLineId(),
                            line.materialCode(),
                            line.materialName(),
                            line.color(),
                            line.unitOfMeasure(),
                            line.lengthMm(),
                            line.quantity(),
                            MaterialReferenceId.of(resolution.materialReferenceId()),
                            null,
                            null);
        };
    }

    private void assertPayloadMatches(
            WarehouseDemand existing, AcceptProductionDemandCommand command) {
        if (!existing.destinationWarehouseId().value().equals(command.destinationWarehouseId())) {
            throw conflict(existing, command, "destinationWarehouseId mismatch");
        }
        if (existing.lines().size() != command.lines().size()) {
            throw conflict(existing, command, "line count mismatch");
        }
        Map<UUID, WarehouseDemandLine> bySource = new LinkedHashMap<>();
        for (WarehouseDemandLine line : existing.lines()) {
            bySource.put(line.sourceMaterialRequirementLineId(), line);
        }
        for (ProductionDemandLine incoming : command.lines()) {
            WarehouseDemandLine existingLine = bySource.get(incoming.sourceMaterialRequirementLineId());
            if (existingLine == null) {
                throw conflict(
                        existing,
                        command,
                        "unknown source line " + incoming.sourceMaterialRequirementLineId());
            }
            if (!existingLine.materialCode().equals(incoming.materialCode().trim())) {
                throw conflict(existing, command, "materialCode mismatch");
            }
            String existingName =
                    existingLine.materialName() == null ? "" : existingLine.materialName();
            String incomingName =
                    incoming.materialName() == null ? "" : incoming.materialName().trim();
            if (!existingName.equals(incomingName)) {
                throw conflict(existing, command, "materialName mismatch");
            }
            String existingColor = existingLine.color() == null ? "" : existingLine.color();
            String incomingColor = incoming.color() == null ? "" : incoming.color().trim();
            if (!existingColor.equals(incomingColor)) {
                throw conflict(existing, command, "color mismatch");
            }
            if (!existingLine.unitOfMeasure().equals(incoming.unitOfMeasure().trim())) {
                throw conflict(existing, command, "unitOfMeasure mismatch");
            }
            if (!Objects.equals(
                    existingLine.lengthMm().orElse(null), incoming.lengthMm())) {
                throw conflict(existing, command, "lengthMm mismatch");
            }
            if (existingLine.requiredQuantity().value().compareTo(incoming.quantity()) != 0) {
                throw conflict(existing, command, "quantity mismatch");
            }
        }
    }

    private static WarehouseDemandPayloadConflictException conflict(
            WarehouseDemand existing, AcceptProductionDemandCommand command, String detail) {
        return new WarehouseDemandPayloadConflictException(
                command.sourceMaterialRequirementId(), existing.id().value(), detail);
    }

    private AcceptProductionDemandResult toResult(WarehouseDemand demand, boolean created) {
        List<DemandLineOutcome> outcomes = new ArrayList<>(demand.lines().size());
        Map<UUID, GeneratedDocument> documentsById = new LinkedHashMap<>();
        for (WarehouseDemandLine line : demand.lines()) {
            List<WarehouseDemandTransferLink> links =
                    demands.findTransferLinksByDemandLineId(line.id());
            if (links.isEmpty()) {
                outcomes.add(toOutcome(line, null));
                continue;
            }
            WarehouseDemandTransferLink link =
                    links.stream()
                            .sorted(Comparator.comparing(l -> l.transferLineId().value()))
                            .findFirst()
                            .orElseThrow();
            PlannedTransferLine planned =
                    new PlannedTransferLine(
                            line,
                            null,
                            link.transferLineId().value(),
                            link.linkedQuantity().value(),
                            link.transferDocumentId());
            outcomes.add(toOutcome(line, planned));
            if (!documentsById.containsKey(link.transferDocumentId())) {
                var document =
                        transferDocumentRepository
                                .findByDocumentId(link.transferDocumentId())
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "Transfer document missing for demand link: "
                                                                + link.transferDocumentId()));
                documentsById.put(
                        link.transferDocumentId(),
                        new GeneratedDocument(
                                link.transferDocumentId(),
                                document.sourceWarehouseId().value(),
                                document.destinationWarehouseId().value()));
            }
        }
        return new AcceptProductionDemandResult(
                demand.id().value(), created, outcomes, List.copyOf(documentsById.values()));
    }

    private static DemandLineOutcome toOutcome(
            WarehouseDemandLine demandLine, PlannedTransferLine planned) {
        DemandLineRoutingOutcome outcome;
        if (planned != null) {
            outcome = DemandLineRoutingOutcome.ROUTED;
        } else if (demandLine.waitingReason().isEmpty()) {
            outcome = DemandLineRoutingOutcome.NO_AVAILABLE_STOCK;
        } else {
            outcome =
                    switch (demandLine.waitingReason().orElseThrow()) {
                        case MATERIAL_UNMATCHED -> DemandLineRoutingOutcome.MATERIAL_UNMATCHED;
                        case MATERIAL_AMBIGUOUS -> DemandLineRoutingOutcome.MATERIAL_AMBIGUOUS;
                        case NO_AVAILABLE_STOCK, ROUTING_DEFERRED ->
                                DemandLineRoutingOutcome.NO_AVAILABLE_STOCK;
                    };
        }
        return new DemandLineOutcome(
                demandLine.sourceMaterialRequirementLineId(),
                demandLine.id().value(),
                demandLine.materialReferenceId().map(MaterialReferenceId::value).orElse(null),
                outcome,
                planned == null ? null : planned.documentId(),
                planned == null ? null : planned.transferLineId(),
                planned == null ? null : planned.linkedQuantity());
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

    private record PreparedLine(
            UUID sourceLineId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal lengthMm,
            BigDecimal quantity,
            MaterialReferenceId materialReferenceId,
            WarehouseDemandWaitingReason waitingReason,
            UUID demandLineId) {

        PreparedLine withWaiting(WarehouseDemandWaitingReason reason) {
            return new PreparedLine(
                    sourceLineId,
                    materialCode,
                    materialName,
                    color,
                    unitOfMeasure,
                    lengthMm,
                    quantity,
                    materialReferenceId,
                    reason,
                    demandLineId);
        }

        PreparedLine withDemandLineId(com.tmp.warehouse.domain.WarehouseDemandLineId id) {
            return new PreparedLine(
                    sourceLineId,
                    materialCode,
                    materialName,
                    color,
                    unitOfMeasure,
                    lengthMm,
                    quantity,
                    materialReferenceId,
                    waitingReason,
                    id.value());
        }
    }

    private record PlannedTransferLine(
            WarehouseDemandLine demandLine,
            MaterialSourceRoutingResult routing,
            UUID transferLineId,
            BigDecimal linkedQuantity,
            UUID documentId) {

        PlannedTransferLine(
                WarehouseDemandLine demandLine,
                MaterialSourceRoutingResult routing,
                UUID transferLineId,
                BigDecimal linkedQuantity) {
            this(demandLine, routing, transferLineId, linkedQuantity, null);
        }

        PlannedTransferLine withDocument(UUID documentId) {
            return new PlannedTransferLine(
                    demandLine, routing, transferLineId, linkedQuantity, documentId);
        }
    }
}
