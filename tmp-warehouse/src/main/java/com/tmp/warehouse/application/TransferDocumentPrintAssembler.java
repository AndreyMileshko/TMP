package com.tmp.warehouse.application;

import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentStatus;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintLineView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintPlannedCellAllocation;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintView;
import com.tmp.warehouse.api.WarehouseApi.TransferPrintAllocationKind;
import com.tmp.warehouse.api.WarehouseApi.TransferPrintDocumentKind;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.TransferDocumentSendAllocation;
import com.tmp.warehouse.domain.TransferReturnSettlementItem;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.WarehouseTransferLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure read-only assembler for approved Warehouse print projections (picking and material return).
 * Does not touch persistence or stock. Receiving has no printable form.
 */
public final class TransferDocumentPrintAssembler {

    public static final String HEADING_PICKING = "ЗАДАНИЕ НА КОМПЛЕКТАЦИЮ МАТЕРИАЛОВ";
    public static final String HEADING_RETURN = "ЗАДАНИЕ НА ВОЗВРАТ МАТЕРИАЛОВ";
    public static final String NOTE_PLANNED_PICKING =
            "Ячейки отбора — плановые (документ ещё не передан).";
    public static final String NOTE_PLANNED_RETURN =
            "Ячейки возврата — плановые (возврат ещё не выполнен).";

    private TransferDocumentPrintAssembler() {}

    public record MaterialDisplay(
            String article, String materialName, String color, String size, String unitOfMeasure) {

        public MaterialDisplay {
            article = article == null ? "" : article;
            materialName = materialName == null ? "" : materialName;
            color = color == null ? "" : color;
            size = size == null ? "" : size;
            unitOfMeasure = unitOfMeasure == null ? "" : unitOfMeasure;
        }

        public static final MaterialDisplay EMPTY = new MaterialDisplay("", "", "", "", "");
    }

    public static TransferDocumentPrintView assemble(
            DocumentMetadata metadata,
            WarehouseTransferDocument payload,
            Warehouse sourceWarehouse,
            Warehouse destinationWarehouse,
            List<String> orderNumbers,
            TransferPrintDocumentKind documentKind,
            List<TransferDocumentSendAllocation> sendAllocations,
            List<TransferReturnSettlementItem> returnItems,
            Map<UUID, StorageCell> cellsById,
            Map<UUID, MaterialDisplay> materialsById,
            List<TransferDocumentPrintPlannedCellAllocation> plannedCellAllocations) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(sourceWarehouse, "sourceWarehouse");
        Objects.requireNonNull(destinationWarehouse, "destinationWarehouse");
        Objects.requireNonNull(documentKind, "documentKind");
        Objects.requireNonNull(cellsById, "cellsById");
        Objects.requireNonNull(materialsById, "materialsById");

        return switch (documentKind) {
            case TRANSFER_PICKING ->
                    assemblePicking(
                            metadata,
                            payload,
                            sourceWarehouse,
                            destinationWarehouse,
                            orderNumbers,
                            sendAllocations == null ? List.of() : sendAllocations,
                            cellsById,
                            materialsById,
                            plannedCellAllocations == null
                                    ? List.of()
                                    : plannedCellAllocations);
            case MATERIAL_RETURN ->
                    assembleReturn(
                            metadata,
                            payload,
                            sourceWarehouse,
                            destinationWarehouse,
                            orderNumbers,
                            sendAllocations == null ? List.of() : sendAllocations,
                            returnItems == null ? List.of() : returnItems,
                            cellsById,
                            materialsById,
                            plannedCellAllocations == null
                                    ? List.of()
                                    : plannedCellAllocations);
        };
    }

    private static TransferDocumentPrintView assemblePicking(
            DocumentMetadata metadata,
            WarehouseTransferDocument payload,
            Warehouse sourceWarehouse,
            Warehouse destinationWarehouse,
            List<String> orderNumbers,
            List<TransferDocumentSendAllocation> sendAllocations,
            Map<UUID, StorageCell> cellsById,
            Map<UUID, MaterialDisplay> materialsById,
            List<TransferDocumentPrintPlannedCellAllocation> planned) {
        // Active preparation is DRAFT (UI planned cells). Historical SEND uses persisted allocations.
        boolean usePlanned =
                metadata.status() == DocumentStatus.DRAFT || sendAllocations.isEmpty();
        TransferPrintAllocationKind kind =
                usePlanned
                        ? TransferPrintAllocationKind.PLANNED
                        : TransferPrintAllocationKind.CONFIRMED;
        List<TransferDocumentPrintLineView> lines =
                usePlanned
                        ? buildPlannedLines(payload, planned, cellsById, materialsById)
                        : buildConfirmedPickingLines(
                                payload, sendAllocations, cellsById, materialsById);
        return new TransferDocumentPrintView(
                metadata.id(),
                metadata.documentNumber(),
                metadata.createdAt(),
                metadata.status().name(),
                metadata.title(),
                TransferPrintDocumentKind.TRANSFER_PICKING,
                HEADING_PICKING,
                orderNumbers == null ? List.of() : orderNumbers,
                sourceWarehouse.id().value(),
                sourceWarehouse.code(),
                sourceWarehouse.name(),
                destinationWarehouse.id().value(),
                destinationWarehouse.code(),
                destinationWarehouse.name(),
                kind,
                usePlanned,
                usePlanned ? NOTE_PLANNED_PICKING : null,
                lines);
    }

    private static TransferDocumentPrintView assembleReturn(
            DocumentMetadata metadata,
            WarehouseTransferDocument payload,
            Warehouse sourceWarehouse,
            Warehouse destinationWarehouse,
            List<String> orderNumbers,
            List<TransferDocumentSendAllocation> sendAllocations,
            List<TransferReturnSettlementItem> returnItems,
            Map<UUID, StorageCell> cellsById,
            Map<UUID, MaterialDisplay> materialsById,
            List<TransferDocumentPrintPlannedCellAllocation> planned) {
        boolean usePlanned = returnItems.isEmpty();
        TransferPrintAllocationKind kind =
                usePlanned
                        ? TransferPrintAllocationKind.PLANNED
                        : TransferPrintAllocationKind.CONFIRMED;
        List<TransferDocumentPrintLineView> lines =
                usePlanned
                        ? buildPlannedLines(payload, planned, cellsById, materialsById)
                        : buildConfirmedReturnLines(
                                payload,
                                sendAllocations,
                                returnItems,
                                cellsById,
                                materialsById);
        String advisory =
                usePlanned
                        ? NOTE_PLANNED_RETURN
                        : "Возврат по документу № " + metadata.documentNumber();
        // Return destination is the original source warehouse (cells for placement).
        return new TransferDocumentPrintView(
                metadata.id(),
                metadata.documentNumber(),
                metadata.createdAt(),
                metadata.status().name(),
                metadata.title(),
                TransferPrintDocumentKind.MATERIAL_RETURN,
                HEADING_RETURN,
                orderNumbers == null ? List.of() : orderNumbers,
                destinationWarehouse.id().value(),
                destinationWarehouse.code(),
                destinationWarehouse.name(),
                sourceWarehouse.id().value(),
                sourceWarehouse.code(),
                sourceWarehouse.name(),
                kind,
                usePlanned,
                advisory,
                lines);
    }

    private static List<TransferDocumentPrintLineView> buildPlannedLines(
            WarehouseTransferDocument payload,
            List<TransferDocumentPrintPlannedCellAllocation> planned,
            Map<UUID, StorageCell> cellsById,
            Map<UUID, MaterialDisplay> materialsById) {
        Map<UUID, List<TransferDocumentPrintPlannedCellAllocation>> byLine = new HashMap<>();
        for (TransferDocumentPrintPlannedCellAllocation row : planned) {
            byLine.computeIfAbsent(row.lineId(), id -> new ArrayList<>()).add(row);
        }
        List<TransferDocumentPrintLineView> rows = new ArrayList<>();
        int rowNumber = 1;
        for (WarehouseTransferLine line : payload.orderedLines()) {
            MaterialDisplay material =
                    materialsById.getOrDefault(
                            line.materialReferenceId().value(), MaterialDisplay.EMPTY);
            List<TransferDocumentPrintPlannedCellAllocation> plannedForLine =
                    byLine.getOrDefault(line.id().value(), List.of());
            if (plannedForLine.isEmpty()) {
                rows.add(
                        lineView(
                                rowNumber++,
                                line,
                                material,
                                line.quantity().value(),
                                null,
                                null,
                                TransferPrintAllocationKind.PLANNED));
                continue;
            }
            for (TransferDocumentPrintPlannedCellAllocation plan : plannedForLine) {
                StorageCell cell =
                        plan.storageCellId() == null
                                ? null
                                : cellsById.get(plan.storageCellId());
                rows.add(
                        lineView(
                                rowNumber++,
                                line,
                                material,
                                plan.quantity(),
                                cell == null ? null : cell.id().value(),
                                cell == null ? null : cell.code(),
                                TransferPrintAllocationKind.PLANNED));
            }
        }
        return List.copyOf(rows);
    }

    private static List<TransferDocumentPrintLineView> buildConfirmedPickingLines(
            WarehouseTransferDocument payload,
            List<TransferDocumentSendAllocation> sendAllocations,
            Map<UUID, StorageCell> cellsById,
            Map<UUID, MaterialDisplay> materialsById) {
        Map<UUID, List<TransferDocumentSendAllocation>> byLine = new HashMap<>();
        for (TransferDocumentSendAllocation allocation : sendAllocations) {
            byLine.computeIfAbsent(allocation.lineId().value(), id -> new ArrayList<>())
                    .add(allocation);
        }
        List<TransferDocumentPrintLineView> rows = new ArrayList<>();
        int rowNumber = 1;
        for (WarehouseTransferLine line : payload.orderedLines()) {
            MaterialDisplay material =
                    materialsById.getOrDefault(
                            line.materialReferenceId().value(), MaterialDisplay.EMPTY);
            List<TransferDocumentSendAllocation> lineAllocations =
                    byLine.getOrDefault(line.id().value(), List.of());
            if (lineAllocations.isEmpty()) {
                rows.add(
                        lineView(
                                rowNumber++,
                                line,
                                material,
                                line.quantity().value(),
                                null,
                                null,
                                TransferPrintAllocationKind.CONFIRMED));
                continue;
            }
            for (TransferDocumentSendAllocation allocation : lineAllocations) {
                StorageCell cell =
                        cellsById.get(allocation.sourceStorageCellId().value());
                rows.add(
                        lineView(
                                rowNumber++,
                                line,
                                material,
                                allocation.quantity().value(),
                                allocation.sourceStorageCellId().value(),
                                cell == null ? null : cell.code(),
                                TransferPrintAllocationKind.CONFIRMED));
            }
        }
        return List.copyOf(rows);
    }

    private static List<TransferDocumentPrintLineView> buildConfirmedReturnLines(
            WarehouseTransferDocument payload,
            List<TransferDocumentSendAllocation> sendAllocations,
            List<TransferReturnSettlementItem> returnItems,
            Map<UUID, StorageCell> cellsById,
            Map<UUID, MaterialDisplay> materialsById) {
        Map<UUID, TransferDocumentSendAllocation> sendById = new HashMap<>();
        for (TransferDocumentSendAllocation allocation : sendAllocations) {
            sendById.put(allocation.id(), allocation);
        }
        Map<UUID, WarehouseTransferLine> linesById = new HashMap<>();
        for (WarehouseTransferLine line : payload.orderedLines()) {
            linesById.put(line.id().value(), line);
        }
        List<TransferDocumentPrintLineView> rows = new ArrayList<>();
        int rowNumber = 1;
        for (TransferReturnSettlementItem item : returnItems) {
            TransferDocumentSendAllocation send = sendById.get(item.sendAllocationId());
            if (send == null) {
                continue;
            }
            WarehouseTransferLine line = linesById.get(send.lineId().value());
            if (line == null) {
                continue;
            }
            MaterialDisplay material =
                    materialsById.getOrDefault(
                            line.materialReferenceId().value(), MaterialDisplay.EMPTY);
            StorageCell cell = cellsById.get(item.returnStorageCellId().value());
            rows.add(
                    lineView(
                            rowNumber++,
                            line,
                            material,
                            item.quantity().value(),
                            item.returnStorageCellId().value(),
                            cell == null ? null : cell.code(),
                            TransferPrintAllocationKind.CONFIRMED));
        }
        return List.copyOf(rows);
    }

    private static TransferDocumentPrintLineView lineView(
            int rowNumber,
            WarehouseTransferLine line,
            MaterialDisplay material,
            BigDecimal quantity,
            UUID storageCellId,
            String storageCellCode,
            TransferPrintAllocationKind kind) {
        return new TransferDocumentPrintLineView(
                rowNumber,
                line.id().value(),
                line.materialReferenceId().value(),
                material.article(),
                material.materialName(),
                material.color(),
                material.size(),
                material.unitOfMeasure(),
                quantity,
                storageCellId,
                storageCellCode,
                kind);
    }

    public static Map<UUID, StorageCell> indexCells(Iterable<StorageCell> cells) {
        Map<UUID, StorageCell> map = new HashMap<>();
        for (StorageCell cell : cells) {
            map.put(cell.id().value(), cell);
        }
        return map;
    }
}