package com.tmp.production.application;

import com.tmp.production.application.MaterialReceiptConfirmationResult.MaterialReceiptConfirmationStatus;
import com.tmp.production.application.MaterialReceiptConfirmationResult.MaterialReceiptReferenceResult;
import com.tmp.production.domain.MaterialReceiptConfirmationException;
import com.tmp.production.domain.ProductionMaterialTransfer;
import com.tmp.production.domain.ProductionMaterialTransferId;
import com.tmp.production.domain.ProductionMaterialTransferNotFoundException;
import com.tmp.production.domain.WarehouseTransferOperationRef;
import com.tmp.production.domain.repository.ProductionMaterialTransferRepository;
import com.tmp.warehouse.api.WarehouseApi.OperationKind;
import com.tmp.warehouse.api.WarehouseApi.OperationResult;
import com.tmp.warehouse.api.WarehouseApi.TransferStatusView;
import com.tmp.warehouse.api.WarehouseCommandApi;
import com.tmp.warehouse.api.WarehouseQueryApi;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Confirms physical receipt of a Production logical material transfer by initiating Warehouse-owned
 * {@code receiveTransfer} (Production Spec §14 / ADR-035 / ADR-036).
 *
 * <p>Does not write Stock Position, create a Production receipt document, or persist a second
 * Warehouse transfer lifecycle. Warehouse remains the sole owner of DRAFT/SENT/RECEIVED.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected repository, Warehouse public APIs, TX manager and clock.")
public final class ConfirmMaterialReceiptService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_RECEIVED = "RECEIVED";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final ProductionMaterialTransferRepository transferRepository;
    private final WarehouseCommandApi warehouseCommandApi;
    private final WarehouseQueryApi warehouseQueryApi;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ConfirmMaterialReceiptService(
            ProductionMaterialTransferRepository transferRepository,
            WarehouseCommandApi warehouseCommandApi,
            WarehouseQueryApi warehouseQueryApi,
            PlatformTransactionManager transactionManager,
            Clock clock) {
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
     * Confirms receipt for the given Production logical transfer. Idempotent when all Warehouse
     * refs are already {@code RECEIVED}.
     */
    public MaterialReceiptConfirmationResult confirmMaterialReceipt(
            ConfirmMaterialReceiptCommand command) {
        Objects.requireNonNull(command, "command");
        MaterialReceiptConfirmationResult result =
                transactionTemplate.execute(status -> confirmInTransaction(command));
        if (result == null) {
            throw new MaterialReceiptConfirmationException(
                    "Receipt confirmation returned null for logicalTransferId="
                            + command.logicalTransferId());
        }
        return result;
    }

    private MaterialReceiptConfirmationResult confirmInTransaction(
            ConfirmMaterialReceiptCommand command) {
        ProductionMaterialTransfer transfer = requireTransfer(command.logicalTransferId());
        List<WarehouseTransferOperationRef> refs = transfer.warehouseOperationRefs();

        List<ValidatedWarehouseStatus> statuses = new ArrayList<>(refs.size());
        for (WarehouseTransferOperationRef ref : refs) {
            TransferStatusView view =
                    warehouseQueryApi.getTransferStatus(ref.warehouseDraftOperationId());
            validateStoredRefAgainstWarehouse(ref, view);
            statuses.add(new ValidatedWarehouseStatus(ref, view, classify(view.status())));
        }

        rejectIfAnyDraft(statuses);
        rejectIfAnyInvalid(statuses);

        Instant confirmedAt = clock.instant();
        if (statuses.stream().allMatch(s -> s.classified() == ClassifiedStatus.RECEIVED)) {
            return buildAlreadyReceived(transfer, statuses, confirmedAt);
        }

        List<MaterialReceiptReferenceResult> referenceResults = new ArrayList<>(statuses.size());
        for (ValidatedWarehouseStatus status : statuses) {
            if (status.classified() == ClassifiedStatus.RECEIVED) {
                referenceResults.add(alreadyReceivedReference(status));
                continue;
            }
            referenceResults.add(receiveSentReference(status));
        }
        return new MaterialReceiptConfirmationResult(
                transfer.logicalTransferId(),
                transfer.sourceOrderId(),
                confirmedAt,
                MaterialReceiptConfirmationStatus.RECEIVED,
                referenceResults);
    }

    private ProductionMaterialTransfer requireTransfer(ProductionMaterialTransferId logicalTransferId) {
        return transferRepository
                .findById(logicalTransferId)
                .orElseThrow(
                        () -> new ProductionMaterialTransferNotFoundException(logicalTransferId));
    }

    private static void rejectIfAnyDraft(List<ValidatedWarehouseStatus> statuses) {
        List<UUID> draftIds =
                statuses.stream()
                        .filter(s -> s.classified() == ClassifiedStatus.DRAFT)
                        .map(s -> s.ref().warehouseDraftOperationId())
                        .toList();
        if (!draftIds.isEmpty()) {
            throw new MaterialReceiptConfirmationException(
                    "Material receipt not ready: Warehouse transfer still DRAFT (not physically"
                            + " sent): "
                            + draftIds);
        }
    }

    private static void rejectIfAnyInvalid(List<ValidatedWarehouseStatus> statuses) {
        List<String> invalid =
                statuses.stream()
                        .filter(s -> s.classified() == ClassifiedStatus.INVALID)
                        .map(
                                s ->
                                        s.ref().warehouseDraftOperationId()
                                                + "="
                                                + s.view().status())
                        .toList();
        if (!invalid.isEmpty()) {
            throw new MaterialReceiptConfirmationException(
                    "Material receipt rejected: unsupported Warehouse transfer status: " + invalid);
        }
    }

    private MaterialReceiptReferenceResult receiveSentReference(ValidatedWarehouseStatus status) {
        WarehouseTransferOperationRef ref = status.ref();
        UUID sendId = ref.warehouseDraftOperationId();
        OperationResult receiveResult = warehouseCommandApi.receiveTransfer(sendId);
        validateReceiveResult(ref, receiveResult);

        TransferStatusView afterReceive = warehouseQueryApi.getTransferStatus(sendId);
        validateStoredRefAgainstWarehouse(ref, afterReceive);
        if (!STATUS_RECEIVED.equals(afterReceive.status())) {
            throw new MaterialReceiptConfirmationException(
                    "Warehouse transfer status after receive must be RECEIVED, got: "
                            + afterReceive.status()
                            + " for sendOperationId="
                            + sendId);
        }
        if (afterReceive.receiveOperationId() == null) {
            throw new MaterialReceiptConfirmationException(
                    "Warehouse getTransferStatus must return receiveOperationId after receive for"
                            + " sendOperationId="
                            + sendId);
        }
        if (!afterReceive.receiveOperationId().equals(receiveResult.operationId())) {
            throw new MaterialReceiptConfirmationException(
                    "Warehouse receiveOperationId mismatch for sendOperationId=" + sendId);
        }
        return new MaterialReceiptReferenceResult(
                sendId,
                afterReceive.receiveOperationId(),
                ref.materialReferenceId(),
                ref.quantity(),
                STATUS_RECEIVED);
    }

    private static MaterialReceiptReferenceResult alreadyReceivedReference(
            ValidatedWarehouseStatus status) {
        TransferStatusView view = status.view();
        if (view.receiveOperationId() == null) {
            throw new MaterialReceiptConfirmationException(
                    "Warehouse RECEIVED status missing receiveOperationId for operationId="
                            + status.ref().warehouseDraftOperationId());
        }
        return new MaterialReceiptReferenceResult(
                status.ref().warehouseDraftOperationId(),
                view.receiveOperationId(),
                status.ref().materialReferenceId(),
                status.ref().quantity(),
                STATUS_RECEIVED);
    }

    private static MaterialReceiptConfirmationResult buildAlreadyReceived(
            ProductionMaterialTransfer transfer,
            List<ValidatedWarehouseStatus> statuses,
            Instant confirmedAt) {
        List<MaterialReceiptReferenceResult> references =
                statuses.stream().map(ConfirmMaterialReceiptService::alreadyReceivedReference).toList();
        return new MaterialReceiptConfirmationResult(
                transfer.logicalTransferId(),
                transfer.sourceOrderId(),
                confirmedAt,
                MaterialReceiptConfirmationStatus.ALREADY_RECEIVED,
                references);
    }

    private static void validateStoredRefAgainstWarehouse(
            WarehouseTransferOperationRef ref, TransferStatusView view) {
        if (view == null) {
            throw new MaterialReceiptConfirmationException(
                    "Warehouse getTransferStatus returned null for operationId="
                            + ref.warehouseDraftOperationId());
        }
        if (!ref.warehouseDraftOperationId().equals(view.operationId())) {
            throw new MaterialReceiptConfirmationException(
                    "Warehouse transfer operationId mismatch: stored="
                            + ref.warehouseDraftOperationId()
                            + ", warehouse="
                            + view.operationId());
        }
        if (view.materialReferenceId() == null
                || !ref.materialReferenceId().value().equals(view.materialReferenceId())) {
            throw new MaterialReceiptConfirmationException(
                    "Warehouse transfer materialReferenceId mismatch for operationId="
                            + ref.warehouseDraftOperationId()
                            + ": stored="
                            + ref.materialReferenceId().value()
                            + ", warehouse="
                            + view.materialReferenceId());
        }
        if (view.quantity() == null || view.quantity().compareTo(ref.quantity()) != 0) {
            throw new MaterialReceiptConfirmationException(
                    "Warehouse transfer quantity mismatch for operationId="
                            + ref.warehouseDraftOperationId()
                            + ": stored="
                            + ref.quantity()
                            + ", warehouse="
                            + view.quantity());
        }
        if (view.storageCellId() != null
                && !ref.sourceStorageCellId().equals(view.storageCellId())) {
            throw new MaterialReceiptConfirmationException(
                    "Warehouse transfer source storageCellId mismatch for operationId="
                            + ref.warehouseDraftOperationId());
        }
        if (view.destinationStorageCellId() != null
                && !ref.destinationStorageCellId().equals(view.destinationStorageCellId())) {
            throw new MaterialReceiptConfirmationException(
                    "Warehouse transfer destinationStorageCellId mismatch for operationId="
                            + ref.warehouseDraftOperationId());
        }
    }

    private static void validateReceiveResult(
            WarehouseTransferOperationRef ref, OperationResult receiveResult) {
        if (receiveResult == null || receiveResult.operationId() == null) {
            throw new MaterialReceiptConfirmationException(
                    "Warehouse receiveTransfer returned null operationId for sendOperationId="
                            + ref.warehouseDraftOperationId());
        }
        if (receiveResult.kind() != OperationKind.TRANSFER_RECEIVE) {
            throw new MaterialReceiptConfirmationException(
                    "Warehouse receiveTransfer must return TRANSFER_RECEIVE, got: "
                            + receiveResult.kind());
        }
        if (!STATUS_COMPLETED.equals(receiveResult.status())) {
            throw new MaterialReceiptConfirmationException(
                    "Warehouse receiveTransfer must return COMPLETED status, got: "
                            + receiveResult.status());
        }
        if (!ref.materialReferenceId().value().equals(receiveResult.materialReferenceId())) {
            throw new MaterialReceiptConfirmationException(
                    "Warehouse receive materialReferenceId mismatch for sendOperationId="
                            + ref.warehouseDraftOperationId());
        }
        if (receiveResult.quantity().compareTo(ref.quantity()) != 0) {
            throw new MaterialReceiptConfirmationException(
                    "Warehouse receive quantity mismatch for sendOperationId="
                            + ref.warehouseDraftOperationId());
        }
        if (!ref.destinationStorageCellId().equals(receiveResult.storageCellId())) {
            throw new MaterialReceiptConfirmationException(
                    "Warehouse receive destination storageCellId mismatch for sendOperationId="
                            + ref.warehouseDraftOperationId());
        }
    }

    private static ClassifiedStatus classify(String warehouseStatus) {
        if (STATUS_DRAFT.equals(warehouseStatus)) {
            return ClassifiedStatus.DRAFT;
        }
        if (STATUS_SENT.equals(warehouseStatus)) {
            return ClassifiedStatus.SENT;
        }
        if (STATUS_RECEIVED.equals(warehouseStatus)) {
            return ClassifiedStatus.RECEIVED;
        }
        return ClassifiedStatus.INVALID;
    }

    private enum ClassifiedStatus {
        DRAFT,
        SENT,
        RECEIVED,
        INVALID
    }

    private record ValidatedWarehouseStatus(
            WarehouseTransferOperationRef ref,
            TransferStatusView view,
            ClassifiedStatus classified) {}
}
