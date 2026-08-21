package com.tmp.production.application;

import com.tmp.production.application.ConfirmMaterialTransferCommand.CellAllocation;
import com.tmp.production.domain.MaterialTransferConfirmationException;
import com.tmp.production.domain.MaterialTransferTemplate;
import com.tmp.production.domain.MaterialTransferTemplateId;
import com.tmp.production.domain.MaterialTransferTemplateLine;
import com.tmp.production.domain.MaterialTransferTemplateLineId;
import com.tmp.production.domain.MaterialTransferTemplateStatus;
import com.tmp.production.domain.ProductionMaterialTransfer;
import com.tmp.production.domain.WarehouseTransferOperationRef;
import com.tmp.production.domain.repository.MaterialTransferTemplateRepository;
import com.tmp.production.domain.repository.ProductionMaterialTransferRepository;
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDraftCommand;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.TransferRequestView;
import com.tmp.warehouse.api.WarehouseCommandApi;
import com.tmp.warehouse.api.WarehouseQueryApi;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Confirms a Material Transfer Template and creates Warehouse-owned Transfer DRAFTs (Production Spec
 * §13 / ADR-035 / ADR-036).
 *
 * <p>Does not call {@code sendTransfer} or {@code receiveTransfer}. Stock is unchanged while drafts
 * remain DRAFT.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected repositories, Warehouse public APIs, TX manager and clock.")
public final class ConfirmMaterialTransferService {

    private final MaterialTransferTemplateRepository templateRepository;
    private final ProductionMaterialTransferRepository transferRepository;
    private final WarehouseCommandApi warehouseCommandApi;
    private final WarehouseQueryApi warehouseQueryApi;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ConfirmMaterialTransferService(
            MaterialTransferTemplateRepository templateRepository,
            ProductionMaterialTransferRepository transferRepository,
            WarehouseCommandApi warehouseCommandApi,
            WarehouseQueryApi warehouseQueryApi,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.templateRepository =
                Objects.requireNonNull(templateRepository, "templateRepository");
        this.transferRepository =
                Objects.requireNonNull(transferRepository, "transferRepository");
        this.warehouseCommandApi =
                Objects.requireNonNull(warehouseCommandApi, "warehouseCommandApi");
        this.warehouseQueryApi = Objects.requireNonNull(warehouseQueryApi, "warehouseQueryApi");
        this.transactionTemplate =
                new TransactionTemplate(
                        Objects.requireNonNull(transactionManager, "transactionManager"));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Confirms the saved template using explicit cell allocations. Idempotent for an already
     * confirmed template: returns the existing logical transfer without creating new Warehouse
     * drafts.
     */
    public ProductionMaterialTransfer confirmMaterialTransferCreate(
            ConfirmMaterialTransferCommand command) {
        Objects.requireNonNull(command, "command");
        ProductionMaterialTransfer result =
                transactionTemplate.execute(status -> confirmInTransaction(command));
        if (result == null) {
            throw new MaterialTransferConfirmationException(
                    "Confirmation returned null for templateId=" + command.templateId());
        }
        return result;
    }

    private ProductionMaterialTransfer confirmInTransaction(ConfirmMaterialTransferCommand command) {
        MaterialTransferTemplate template = requireTemplate(command.templateId());

        if (template.status() == MaterialTransferTemplateStatus.CONFIRMED) {
            return transferRepository
                    .findByTemplateId(template.templateId())
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "Confirmed template missing logical transfer: "
                                                    + template.templateId()));
        }

        if (template.version() != command.expectedVersion()) {
            throw new MaterialTransferConfirmationException(
                    "Stale template version: expected="
                            + command.expectedVersion()
                            + ", actual="
                            + template.version());
        }

        List<MaterialTransferTemplateLine> included = template.includedTransferLines();
        if (included.isEmpty()) {
            throw new MaterialTransferConfirmationException(
                    "Template has no included lines to transfer: " + template.templateId());
        }

        Map<MaterialTransferTemplateLineId, MaterialTransferTemplateLine> includedById =
                new LinkedHashMap<>();
        for (MaterialTransferTemplateLine line : included) {
            includedById.put(line.lineId(), line);
        }

        Map<MaterialTransferTemplateLineId, List<CellAllocation>> allocationsByLine =
                validateAndGroupAllocations(command.allocations(), template, includedById);

        Map<UUID, StorageCellView> sourceCells =
                indexActiveCells(
                        warehouseQueryApi.listStorageCells(template.sourceWarehouseId()),
                        template.sourceWarehouseId(),
                        "source");
        Map<UUID, StorageCellView> destinationCells =
                indexActiveCells(
                        warehouseQueryApi.listStorageCells(template.destinationWarehouseId()),
                        template.destinationWarehouseId(),
                        "destination");

        List<WarehouseTransferOperationRef> refs = new ArrayList<>();
        for (MaterialTransferTemplateLine line : included) {
            List<CellAllocation> lineAllocations = allocationsByLine.get(line.lineId());
            for (CellAllocation allocation : lineAllocations) {
                requireCell(
                        sourceCells,
                        allocation.sourceStorageCellId(),
                        template.sourceWarehouseId(),
                        "source");
                requireCell(
                        destinationCells,
                        allocation.destinationStorageCellId(),
                        template.destinationWarehouseId(),
                        "destination");

                TransferRequestView draft =
                        warehouseCommandApi.createTransferDraft(
                                new CreateTransferDraftCommand(
                                        line.materialReferenceId().value(),
                                        allocation.quantity(),
                                        template.sourceWarehouseId(),
                                        allocation.sourceStorageCellId(),
                                        template.destinationWarehouseId(),
                                        allocation.destinationStorageCellId()));
                validateDraftResult(draft, line, allocation, template);
                refs.add(
                        new WarehouseTransferOperationRef(
                                line.lineId(),
                                draft.operationId(),
                                line.materialReferenceId(),
                                allocation.quantity(),
                                allocation.sourceStorageCellId(),
                                allocation.destinationStorageCellId()));
            }
        }

        Instant now = clock.instant();
        ProductionMaterialTransfer transfer =
                ProductionMaterialTransfer.create(
                        template.templateId(), template.sourceOrderId(), now, refs);
        ProductionMaterialTransfer savedTransfer = transferRepository.save(transfer);
        templateRepository.save(template.confirm(now));
        return savedTransfer;
    }

    private MaterialTransferTemplate requireTemplate(MaterialTransferTemplateId templateId) {
        return templateRepository
                .findById(templateId)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Material transfer template not found: " + templateId));
    }

    private static Map<MaterialTransferTemplateLineId, List<CellAllocation>>
            validateAndGroupAllocations(
                    List<CellAllocation> allocations,
                    MaterialTransferTemplate template,
                    Map<MaterialTransferTemplateLineId, MaterialTransferTemplateLine> includedById) {
        if (allocations.isEmpty()) {
            throw new MaterialTransferConfirmationException(
                    "At least one cell allocation is required");
        }

        Map<MaterialTransferTemplateLineId, MaterialTransferTemplateLine> allLines =
                new HashMap<>();
        for (MaterialTransferTemplateLine line : template.lines()) {
            allLines.put(line.lineId(), line);
        }

        Set<String> uniqueKeys = new HashSet<>();
        Map<MaterialTransferTemplateLineId, List<CellAllocation>> byLine = new LinkedHashMap<>();
        Map<MaterialTransferTemplateLineId, BigDecimal> totals = new HashMap<>();

        for (CellAllocation allocation : allocations) {
            MaterialTransferTemplateLine line = allLines.get(allocation.templateLineId());
            if (line == null) {
                throw new MaterialTransferConfirmationException(
                        "Unknown template lineId: " + allocation.templateLineId());
            }
            if (!line.included()) {
                throw new MaterialTransferConfirmationException(
                        "Allocation for excluded line is not allowed: " + line.lineId());
            }
            String key =
                    allocation.templateLineId()
                            + "|"
                            + allocation.sourceStorageCellId()
                            + "|"
                            + allocation.destinationStorageCellId();
            if (!uniqueKeys.add(key)) {
                throw new MaterialTransferConfirmationException(
                        "Duplicate cell allocation for line "
                                + allocation.templateLineId()
                                + " sourceCell="
                                + allocation.sourceStorageCellId()
                                + " destinationCell="
                                + allocation.destinationStorageCellId());
            }
            byLine.computeIfAbsent(allocation.templateLineId(), ignored -> new ArrayList<>())
                    .add(allocation);
            totals.merge(
                    allocation.templateLineId(), allocation.quantity(), BigDecimal::add);
        }

        for (Map.Entry<MaterialTransferTemplateLineId, MaterialTransferTemplateLine> entry :
                includedById.entrySet()) {
            MaterialTransferTemplateLineId lineId = entry.getKey();
            MaterialTransferTemplateLine line = entry.getValue();
            List<CellAllocation> lineAllocations = byLine.get(lineId);
            if (lineAllocations == null || lineAllocations.isEmpty()) {
                throw new MaterialTransferConfirmationException(
                        "Included line requires at least one allocation: " + lineId);
            }
            BigDecimal total = totals.get(lineId);
            if (total.compareTo(line.requestedQuantity()) != 0) {
                throw new MaterialTransferConfirmationException(
                        "Allocation total must equal persisted requestedQuantity for line "
                                + lineId
                                + ": allocations="
                                + total
                                + ", requestedQuantity="
                                + line.requestedQuantity());
            }
        }

        if (byLine.size() != includedById.size()) {
            throw new MaterialTransferConfirmationException(
                    "Allocations must cover exactly the included template lines");
        }
        return byLine;
    }

    private static Map<UUID, StorageCellView> indexActiveCells(
            List<StorageCellView> cells, UUID expectedWarehouseId, String role) {
        Map<UUID, StorageCellView> indexed = new HashMap<>();
        for (StorageCellView cell : cells) {
            if (!expectedWarehouseId.equals(cell.warehouseId())) {
                throw new MaterialTransferConfirmationException(
                        "WarehouseQueryApi returned "
                                + role
                                + " cell for unexpected warehouse: cell="
                                + cell.storageCellId()
                                + ", warehouseId="
                                + cell.warehouseId());
            }
            indexed.put(cell.storageCellId(), cell);
        }
        return indexed;
    }

    private static void requireCell(
            Map<UUID, StorageCellView> cells, UUID cellId, UUID warehouseId, String role) {
        StorageCellView cell = cells.get(cellId);
        if (cell == null) {
            throw new MaterialTransferConfirmationException(
                    role
                            + " storage cell not found in warehouse "
                            + warehouseId
                            + ": "
                            + cellId);
        }
        if (!cell.active()) {
            throw new MaterialTransferConfirmationException(
                    role + " storage cell is inactive: " + cellId);
        }
        if (!warehouseId.equals(cell.warehouseId())) {
            throw new MaterialTransferConfirmationException(
                    role
                            + " storage cell does not belong to warehouse "
                            + warehouseId
                            + ": "
                            + cellId);
        }
    }

    private static void validateDraftResult(
            TransferRequestView draft,
            MaterialTransferTemplateLine line,
            CellAllocation allocation,
            MaterialTransferTemplate template) {
        if (draft == null || draft.operationId() == null) {
            throw new MaterialTransferConfirmationException(
                    "Warehouse createTransferDraft returned null operationId");
        }
        if (!"DRAFT".equals(draft.status())) {
            throw new MaterialTransferConfirmationException(
                    "Warehouse createTransferDraft must return DRAFT status, got: "
                            + draft.status());
        }
        if (!line.materialReferenceId().value().equals(draft.materialReferenceId())) {
            throw new MaterialTransferConfirmationException(
                    "Warehouse draft materialReferenceId mismatch");
        }
        if (draft.quantity().compareTo(allocation.quantity()) != 0) {
            throw new MaterialTransferConfirmationException(
                    "Warehouse draft quantity mismatch");
        }
        if (!template.sourceWarehouseId().equals(draft.sourceWarehouseId())
                || !allocation.sourceStorageCellId().equals(draft.sourceStorageCellId())
                || !template.destinationWarehouseId().equals(draft.destinationWarehouseId())
                || !allocation
                        .destinationStorageCellId()
                        .equals(draft.destinationStorageCellId())) {
            throw new MaterialTransferConfirmationException(
                    "Warehouse draft warehouse/cell mismatch");
        }
    }
}
