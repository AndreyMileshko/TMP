package com.tmp.warehouse.application;

import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentStatus;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskKind;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskState;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import com.tmp.warehouse.application.document.WarehouseTransferDocumentProcessor;
import com.tmp.warehouse.domain.TransferDocumentSettlement;
import com.tmp.warehouse.domain.TransferSettlementState;
import com.tmp.warehouse.domain.TransferTaskAssignment;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
import com.tmp.warehouse.domain.repository.TransferDocumentSettlementRepository;
import com.tmp.warehouse.domain.repository.TransferTaskStateRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository;
import com.tmp.warehouse.domain.repository.WarehouseUserResponsibilityRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Warehouse operational inbox projection (Stage 3.5.5 / 3.5.8.1 / 3.5.8.2).
 *
 * <p>TRANSFER_PREPARATION: DRAFT documents for source-responsible users.
 *
 * <p>TRANSFER_RECEIPT: POSTED + AWAITING_RECEIPT for destination-responsible users.
 *
 * <p>RETURN_MATERIALS: POSTED + RETURN_PENDING for source-responsible users (physical return in
 * 3.5.8.3).
 *
 * <p>Worker assignment is informational (not an exclusive lock).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators.")
public final class WarehouseOperationalInboxService {

    /**
     * Bounded Document Engine scan page size. Inbox assembles all matching tasks by paging until a
     * short page; it does not fake responsibility-aware pagination.
     */
    static final int DOCUMENT_SCAN_PAGE_SIZE = 100;

    private static final Comparator<WarehouseTaskView> TASK_ORDER =
            Comparator.comparingInt((WarehouseTaskView t) -> taskStateRank(t.taskState()))
                    .thenComparing(WarehouseTaskView::createdAt)
                    .thenComparing(WarehouseTaskView::documentId);

    private final DocumentEngine documentEngine;
    private final WarehouseTransferDocumentRepository transferDocuments;
    private final TransferDocumentSettlementRepository settlements;
    private final TransferTaskStateRepository taskStates;
    private final WarehouseUserResponsibilityRepository responsibilities;
    private final WarehouseCatalogRepository warehouses;
    private final WarehouseResponsibilityGuard responsibilityGuard;
    private final AuthenticationService authentication;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public WarehouseOperationalInboxService(
            DocumentEngine documentEngine,
            WarehouseTransferDocumentRepository transferDocuments,
            TransferTaskStateRepository taskStates,
            WarehouseUserResponsibilityRepository responsibilities,
            WarehouseCatalogRepository warehouses,
            WarehouseResponsibilityGuard responsibilityGuard,
            AuthenticationService authentication,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this(
                documentEngine,
                transferDocuments,
                null,
                taskStates,
                responsibilities,
                warehouses,
                responsibilityGuard,
                authentication,
                transactionTemplate,
                clock);
    }

    public WarehouseOperationalInboxService(
            DocumentEngine documentEngine,
            WarehouseTransferDocumentRepository transferDocuments,
            TransferDocumentSettlementRepository settlements,
            TransferTaskStateRepository taskStates,
            WarehouseUserResponsibilityRepository responsibilities,
            WarehouseCatalogRepository warehouses,
            WarehouseResponsibilityGuard responsibilityGuard,
            AuthenticationService authentication,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.transferDocuments = Objects.requireNonNull(transferDocuments, "transferDocuments");
        this.settlements = settlements;
        this.taskStates = Objects.requireNonNull(taskStates, "taskStates");
        this.responsibilities = Objects.requireNonNull(responsibilities, "responsibilities");
        this.warehouses = Objects.requireNonNull(warehouses, "warehouses");
        this.responsibilityGuard =
                Objects.requireNonNull(responsibilityGuard, "responsibilityGuard");
        this.authentication = Objects.requireNonNull(authentication, "authentication");
        this.transactionTemplate =
                Objects.requireNonNull(transactionTemplate, "transactionTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Lists preparation, receipt, and return tasks for the current user's responsible warehouses.
     *
     * @param warehouseIdFilter optional single warehouse filter (must be in responsibility scope)
     */
    public List<WarehouseTaskView> listMyWarehouseTasks(UUID warehouseIdFilter) {
        UUID userId = requireAuthenticatedUserId();
        Set<UUID> responsible = responsibleWarehouseIds(userId);
        if (warehouseIdFilter != null) {
            if (!responsible.contains(warehouseIdFilter)) {
                throw new AccessDeniedException(
                        "Access denied: not responsible for warehouse " + warehouseIdFilter);
            }
            responsible = Set.of(warehouseIdFilter);
        }
        if (responsible.isEmpty()) {
            return List.of();
        }

        List<DocumentMetadata> drafts = scanTransferDocuments(DocumentStatus.DRAFT);
        List<DocumentMetadata> posted = scanTransferDocuments(DocumentStatus.POSTED);
        List<UUID> allIds = new ArrayList<>();
        drafts.forEach(d -> allIds.add(d.id()));
        posted.forEach(d -> allIds.add(d.id()));
        if (allIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, WarehouseTransferDocument> payloads = transferDocuments.findByDocumentIds(allIds);
        Map<UUID, TransferTaskAssignment> assignments = taskStates.findByDocumentIds(allIds);
        Map<UUID, TransferDocumentSettlement> settlementById =
                settlements == null
                        ? Map.of()
                        : settlements.findByDocumentIds(
                                posted.stream().map(DocumentMetadata::id).toList());
        Map<UUID, Warehouse> warehouseById = warehouseIndex();

        List<WarehouseTaskView> tasks = new ArrayList<>();
        for (DocumentMetadata metadata : drafts) {
            WarehouseTransferDocument payload = payloads.get(metadata.id());
            if (payload == null) {
                continue;
            }
            if (!responsible.contains(payload.sourceWarehouseId().value())) {
                continue;
            }
            tasks.add(
                    toTaskView(
                            metadata,
                            payload,
                            WarehouseTaskKind.TRANSFER_PREPARATION,
                            assignments.get(metadata.id()),
                            null,
                            warehouseById));
        }
        for (DocumentMetadata metadata : posted) {
            WarehouseTransferDocument payload = payloads.get(metadata.id());
            if (payload == null) {
                continue;
            }
            TransferDocumentSettlement settlement = settlementById.get(metadata.id());
            if (settlement == null) {
                continue;
            }
            if (settlement.settlementState() == TransferSettlementState.AWAITING_RECEIPT) {
                if (!responsible.contains(payload.destinationWarehouseId().value())) {
                    continue;
                }
                tasks.add(
                        toTaskView(
                                metadata,
                                payload,
                                WarehouseTaskKind.TRANSFER_RECEIPT,
                                assignments.get(metadata.id()),
                                settlement,
                                warehouseById));
            } else if (settlement.settlementState() == TransferSettlementState.RETURN_PENDING) {
                if (!responsible.contains(payload.sourceWarehouseId().value())) {
                    continue;
                }
                tasks.add(
                        toTaskView(
                                metadata,
                                payload,
                                WarehouseTaskKind.RETURN_MATERIALS,
                                assignments.get(metadata.id()),
                                settlement,
                                warehouseById));
            }
        }
        tasks.sort(TASK_ORDER);
        return List.copyOf(tasks);
    }

    /**
     * Informational take-in-work / takeover. Responsibility scope is derived from the current task
     * phase (source for preparation/return, destination for receipt) — not from a caller-supplied
     * kind.
     */
    public WarehouseTaskView takeTransferTaskInWork(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        UUID userId = requireAuthenticatedUserId();
        Instant now = clock.instant();

        WarehouseTaskView view =
                transactionTemplate.execute(
                        status -> {
                            DocumentMetadata metadata =
                                    documentEngine
                                            .findById(documentId)
                                            .orElseThrow(
                                                    () ->
                                                            new IllegalArgumentException(
                                                                    "Transfer document not found: "
                                                                            + documentId));
                            if (!WarehouseTransferDocumentProcessor.DOCUMENT_TYPE_ID.equals(
                                    metadata.documentTypeId())) {
                                throw new IllegalArgumentException(
                                        "Not a warehouse.transfer document: " + documentId);
                            }
                            WarehouseTransferDocument payload =
                                    transferDocuments
                                            .findByDocumentId(documentId)
                                            .orElseThrow(
                                                    () ->
                                                            new IllegalArgumentException(
                                                                    "Transfer document payload not found: "
                                                                            + documentId));
                            WarehouseTaskKind kind;
                            TransferDocumentSettlement settlement = null;
                            if (metadata.status() == DocumentStatus.DRAFT) {
                                kind = WarehouseTaskKind.TRANSFER_PREPARATION;
                                responsibilityGuard.requireResponsible(
                                        payload.sourceWarehouseId());
                            } else if (metadata.status() == DocumentStatus.POSTED) {
                                if (settlements == null) {
                                    throw new IllegalStateException(
                                            "Transfer settlement repository is not configured");
                                }
                                settlement =
                                        settlements
                                                .findByDocumentId(documentId)
                                                .orElseThrow(
                                                        () ->
                                                                new IllegalStateException(
                                                                        "Transfer settlement missing for posted task: "
                                                                                + documentId));
                                if (settlement.settlementState()
                                        == TransferSettlementState.AWAITING_RECEIPT) {
                                    kind = WarehouseTaskKind.TRANSFER_RECEIPT;
                                    responsibilityGuard.requireResponsible(
                                            payload.destinationWarehouseId());
                                } else if (settlement.settlementState()
                                        == TransferSettlementState.RETURN_PENDING) {
                                    kind = WarehouseTaskKind.RETURN_MATERIALS;
                                    responsibilityGuard.requireResponsible(
                                            payload.sourceWarehouseId());
                                } else {
                                    throw new IllegalStateException(
                                            "Transfer task take-in-work requires AWAITING_RECEIPT or RETURN_PENDING: documentId="
                                                    + documentId
                                                    + ", state="
                                                    + settlement.settlementState());
                                }
                            } else {
                                throw new IllegalStateException(
                                        "Transfer task take-in-work requires DRAFT or POSTED operational settlement: documentId="
                                                + documentId
                                                + ", status="
                                                + metadata.status());
                            }
                            TransferTaskAssignment assignment =
                                    taskStates.takeInWork(documentId, userId, now);
                            return toTaskView(
                                    metadata,
                                    payload,
                                    kind,
                                    assignment,
                                    settlement,
                                    warehouseIndex());
                        });
        if (view == null) {
            throw new IllegalStateException("takeTransferTaskInWork returned null");
        }
        return view;
    }

    private List<DocumentMetadata> scanTransferDocuments(DocumentStatus status) {
        List<DocumentMetadata> all = new ArrayList<>();
        int page = 0;
        while (true) {
            int offset = page * DOCUMENT_SCAN_PAGE_SIZE;
            List<DocumentMetadata> batch =
                    documentEngine.search(
                            new DocumentQuery(
                                    Optional.of(
                                            WarehouseTransferDocumentProcessor.DOCUMENT_TYPE_ID),
                                    Optional.of(status),
                                    Optional.empty(),
                                    DOCUMENT_SCAN_PAGE_SIZE,
                                    offset));
            all.addAll(batch);
            if (batch.size() < DOCUMENT_SCAN_PAGE_SIZE) {
                break;
            }
            page++;
        }
        return all;
    }

    private Set<UUID> responsibleWarehouseIds(UUID userId) {
        return new HashSet<>(
                responsibilities.listWarehouseIdsForUser(userId).stream()
                        .map(WarehouseId::value)
                        .toList());
    }

    private UUID requireAuthenticatedUserId() {
        return authentication
                .currentSession()
                .orElseThrow(
                        () ->
                                new AccessDeniedException(
                                        "Access denied: authentication required"))
                .userId()
                .value();
    }

    private Map<UUID, Warehouse> warehouseIndex() {
        Map<UUID, Warehouse> byId = new HashMap<>();
        for (Warehouse warehouse : warehouses.findAll()) {
            byId.put(warehouse.id().value(), warehouse);
        }
        return byId;
    }

    private static WarehouseTaskView toTaskView(
            DocumentMetadata metadata,
            WarehouseTransferDocument payload,
            WarehouseTaskKind kind,
            TransferTaskAssignment assignment,
            TransferDocumentSettlement settlement,
            Map<UUID, Warehouse> warehouseById) {
        Warehouse source = warehouseById.get(payload.sourceWarehouseId().value());
        Warehouse destination = warehouseById.get(payload.destinationWarehouseId().value());
        WarehouseTaskState state =
                assignment == null ? WarehouseTaskState.NEW : WarehouseTaskState.IN_WORK;
        return new WarehouseTaskView(
                metadata.id(),
                metadata.documentNumber(),
                kind,
                state,
                payload.sourceWarehouseId().value(),
                source == null ? null : source.code(),
                source == null ? null : source.name(),
                payload.destinationWarehouseId().value(),
                destination == null ? null : destination.code(),
                destination == null ? null : destination.name(),
                payload.orderedLines().size(),
                assignment == null ? null : assignment.workingUserId(),
                assignment == null ? null : assignment.workingSince(),
                metadata.createdAt(),
                payload.continuationOfDocumentId().orElse(null),
                payload.continuationReason().map(Enum::name).orElse(null),
                settlement == null ? null : settlement.settlementState().name(),
                settlement == null ? null : settlement.operationalRevision(),
                settlement == null
                        ? null
                        : settlement.decision().map(Enum::name).orElse(null),
                settlement == null ? null : settlement.rejectionReason().orElse(null));
    }

    private static int taskStateRank(WarehouseTaskState state) {
        return switch (state) {
            case NEW -> 0;
            case IN_WORK -> 1;
        };
    }
}
