package com.tmp.production.application;

import com.tmp.production.domain.MaterialAvailabilityCheckResult;
import com.tmp.production.domain.MaterialAvailabilityLine;
import com.tmp.production.domain.MaterialAvailabilityLineStatus;
import com.tmp.production.domain.MaterialTransferTemplate;
import com.tmp.production.domain.ProductionCancellation;
import com.tmp.production.domain.ProductionHistoryEntry;
import com.tmp.production.domain.ProductionHistoryEntry.ProductionHistoryEntryId;
import com.tmp.production.domain.ProductionHistoryEntry.ProductionHistoryType;
import com.tmp.production.domain.ProductionMaterialTransfer;
import com.tmp.production.domain.ProductionRelease;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.ProductionHistoryRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Production-owned history appender and internal query port (Production Spec §22).
 *
 * <p>Joins ambient REQUIRED transactions for mutating use cases. Does not decide which business
 * facts to record — callers construct validated entries via the factory methods.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected repository and clock.")
public final class ProductionHistoryService {

    private final ProductionHistoryRepository repository;
    private final Clock clock;

    public ProductionHistoryService(ProductionHistoryRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Appends one validated history entry into the ambient transaction. */
    public ProductionHistoryEntry append(ProductionHistoryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return repository.append(entry);
    }

    /**
     * Chronological Production business timeline for one order. Empty for unknown orders.
     */
    public List<ProductionHistoryEntry> listByOrder(SourceOrderId sourceOrderId) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        return repository.listByOrder(sourceOrderId);
    }

    public ProductionHistoryEntry orderAccepted(
            SourceOrderId sourceOrderId,
            UUID launchDocumentId,
            Instant launchTimestamp,
            List<UUID> sourceOrderItemIds,
            Optional<String> actorRef) {
        Objects.requireNonNull(sourceOrderItemIds, "sourceOrderItemIds");
        String itemIdsJson =
                sourceOrderItemIds.stream()
                        .map(id -> "\"" + id + "\"")
                        .collect(Collectors.joining(",", "[", "]"));
        String details =
                "{\"acceptedItemCount\":"
                        + sourceOrderItemIds.size()
                        + ",\"sourceOrderItemIds\":"
                        + itemIdsJson
                        + "}";
        return ProductionHistoryEntry.create(
                ProductionHistoryEntryId.generate(),
                sourceOrderId,
                ProductionHistoryType.ORDER_ACCEPTED,
                launchTimestamp,
                clock.instant(),
                Optional.empty(),
                Optional.of(launchDocumentId),
                Optional.of(launchDocumentId),
                actorRef,
                Optional.of("Order accepted into production"),
                Optional.of(details));
    }

    public ProductionHistoryEntry materialsChecked(MaterialAvailabilityCheckResult result) {
        Objects.requireNonNull(result, "result");
        int available = 0;
        int deficit = 0;
        int unresolved = 0;
        for (MaterialAvailabilityLine line : result.lines()) {
            if (line.status() == MaterialAvailabilityLineStatus.AVAILABLE) {
                available++;
            } else if (line.status() == MaterialAvailabilityLineStatus.INSUFFICIENT) {
                deficit++;
            } else {
                unresolved++;
            }
        }
        String details =
                "{\"overallStatus\":\""
                        + result.overallStatus().name()
                        + "\",\"materialLineCount\":"
                        + result.lines().size()
                        + ",\"availableCount\":"
                        + available
                        + ",\"deficitCount\":"
                        + deficit
                        + ",\"unresolvedOrAmbiguousCount\":"
                        + unresolved
                        + "}";
        return ProductionHistoryEntry.create(
                ProductionHistoryEntryId.generate(),
                result.sourceOrderId(),
                ProductionHistoryType.MATERIALS_CHECKED,
                result.checkedAt(),
                clock.instant(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of("Materials checked: " + result.overallStatus().name()),
                Optional.of(details));
    }

    public ProductionHistoryEntry materialTransferCreated(
            MaterialTransferTemplate template, ProductionMaterialTransfer transfer) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(transfer, "transfer");
        int includedLineCount = template.includedTransferLines().size();
        int operationCount = transfer.warehouseOperationRefs().size();
        String details =
                "{\"templateId\":\""
                        + template.templateId().value()
                        + "\",\"logicalTransferId\":\""
                        + transfer.logicalTransferId().value()
                        + "\",\"includedLineCount\":"
                        + includedLineCount
                        + ",\"warehouseOperationReferenceCount\":"
                        + operationCount
                        + ",\"sourceWarehouseId\":\""
                        + template.sourceWarehouseId()
                        + "\",\"destinationWarehouseId\":\""
                        + template.destinationWarehouseId()
                        + "\"}";
        return ProductionHistoryEntry.create(
                ProductionHistoryEntryId.generate(),
                transfer.sourceOrderId(),
                ProductionHistoryType.MATERIAL_TRANSFER_CREATED,
                transfer.createdAt(),
                clock.instant(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(transfer.logicalTransferId().value()),
                Optional.empty(),
                Optional.of("Material transfer created"),
                Optional.of(details));
    }

    public ProductionHistoryEntry materialReceiptConfirmed(
            ProductionMaterialTransfer transfer,
            Instant confirmedAt,
            int confirmedReferenceCount,
            List<UUID> receiveOperationIds) {
        Objects.requireNonNull(transfer, "transfer");
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        Objects.requireNonNull(receiveOperationIds, "receiveOperationIds");
        String receiveIdsJson =
                receiveOperationIds.stream()
                        .map(id -> "\"" + id + "\"")
                        .collect(Collectors.joining(",", "[", "]"));
        String details =
                "{\"logicalTransferId\":\""
                        + transfer.logicalTransferId().value()
                        + "\",\"confirmedReferenceCount\":"
                        + confirmedReferenceCount
                        + ",\"receiveOperationIds\":"
                        + receiveIdsJson
                        + "}";
        return ProductionHistoryEntry.create(
                ProductionHistoryEntryId.generate(),
                transfer.sourceOrderId(),
                ProductionHistoryType.MATERIAL_RECEIPT_CONFIRMED,
                confirmedAt,
                clock.instant(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(transfer.logicalTransferId().value()),
                Optional.empty(),
                Optional.of("Material receipt confirmed"),
                Optional.of(details));
    }

    public ProductionHistoryEntry productsReleased(
            ProductionRelease release, UUID documentId, List<ReleasedItemSnapshot> items) {
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(items, "items");
        StringBuilder itemsJson = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            ReleasedItemSnapshot item = items.get(i);
            if (i > 0) {
                itemsJson.append(',');
            }
            itemsJson
                    .append("{\"sourceOrderItemId\":\"")
                    .append(item.sourceOrderItemId())
                    .append("\",\"releaseQuantity\":")
                    .append(item.releaseQuantity())
                    .append(",\"resultingStatus\":\"")
                    .append(item.resultingStatus())
                    .append("\"}");
        }
        itemsJson.append(']');
        String details =
                "{\"releasedItemCount\":"
                        + items.size()
                        + ",\"items\":"
                        + itemsJson
                        + "}";
        return ProductionHistoryEntry.create(
                ProductionHistoryEntryId.generate(),
                release.sourceOrderId(),
                ProductionHistoryType.PRODUCTS_RELEASED,
                release.releasedAt(),
                clock.instant(),
                Optional.empty(),
                Optional.of(documentId),
                Optional.of(documentId),
                Optional.empty(),
                Optional.of("Products released"),
                Optional.of(details));
    }

    /**
     * Builds PLAN_FACT_DEVIATION only when at least one material line has numeric plan≠fact.
     * Returns empty when all lines compare equal via {@link BigDecimal#compareTo}.
     */
    public Optional<ProductionHistoryEntry> planFactDeviationIfAny(
            ProductionRelease release, UUID documentId) {
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(documentId, "documentId");
        List<ProductionRelease.MaterialLine> deviations = new ArrayList<>();
        for (ProductionRelease.MaterialLine line : release.materialLines()) {
            if (line.plannedQuantity().compareTo(line.actualQuantity()) != 0) {
                deviations.add(line);
            }
        }
        if (deviations.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder deviationsJson = new StringBuilder("[");
        for (int i = 0; i < deviations.size(); i++) {
            ProductionRelease.MaterialLine line = deviations.get(i);
            if (i > 0) {
                deviationsJson.append(',');
            }
            deviationsJson
                    .append("{\"sourceOrderItemId\":")
                    .append(
                            line.sourceOrderItemId()
                                    .map(id -> "\"" + id.value() + "\"")
                                    .orElse("null"))
                    .append(",\"materialReferenceId\":\"")
                    .append(line.materialReferenceId().value())
                    .append("\",\"plannedQuantity\":")
                    .append(line.plannedQuantity().toPlainString())
                    .append(",\"actualQuantity\":")
                    .append(line.actualQuantity().toPlainString())
                    .append(",\"actualMinusPlanned\":")
                    .append(line.actualMinusPlanned().toPlainString())
                    .append(",\"planningSource\":\"")
                    .append(line.planningSource().name())
                    .append("\"}");
        }
        deviationsJson.append(']');
        String details = "{\"deviations\":" + deviationsJson + "}";
        return Optional.of(
                ProductionHistoryEntry.create(
                        ProductionHistoryEntryId.generate(),
                        release.sourceOrderId(),
                        ProductionHistoryType.PLAN_FACT_DEVIATION,
                        release.releasedAt(),
                        clock.instant(),
                        Optional.empty(),
                        Optional.of(documentId),
                        Optional.of(documentId),
                        Optional.empty(),
                        Optional.of("Plan/fact deviation"),
                        Optional.of(details)));
    }

    public ProductionHistoryEntry productionCancelled(
            ProductionCancellation cancellation, UUID documentId) {
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(documentId, "documentId");
        long cancelledUnfinished =
                cancellation.itemLines().stream()
                        .filter(
                                line ->
                                        line.action()
                                                == com.tmp.production.domain.CancellationItemAction
                                                        .CANCELLED_UNFINISHED)
                        .count();
        long preservedReleased =
                cancellation.itemLines().stream()
                        .filter(
                                line ->
                                        line.action()
                                                == com.tmp.production.domain.CancellationItemAction
                                                        .PRESERVED_RELEASED)
                        .count();
        String reasonJson =
                cancellation
                        .reason()
                        .map(reason -> ",\"reason\":\"" + escapeJson(reason) + "\"")
                        .orElse("");
        String details =
                "{\"cancelledUnfinishedCount\":"
                        + cancelledUnfinished
                        + ",\"preservedReleasedCount\":"
                        + preservedReleased
                        + reasonJson
                        + "}";
        return ProductionHistoryEntry.create(
                ProductionHistoryEntryId.generate(),
                cancellation.sourceOrderId(),
                ProductionHistoryType.PRODUCTION_CANCELLED,
                cancellation.cancelledAt(),
                clock.instant(),
                Optional.empty(),
                Optional.of(documentId),
                Optional.of(documentId),
                Optional.empty(),
                Optional.of("Production cancelled"),
                Optional.of(details));
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Compact release item snapshot for PRODUCTS_RELEASED details. */
    public record ReleasedItemSnapshot(
            UUID sourceOrderItemId, long releaseQuantity, String resultingStatus) {

        public ReleasedItemSnapshot {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(resultingStatus, "resultingStatus");
        }

        public static ReleasedItemSnapshot of(
                SourceOrderItemId itemId, long releaseQuantity, String resultingStatus) {
            return new ReleasedItemSnapshot(itemId.value(), releaseQuantity, resultingStatus);
        }
    }
}
