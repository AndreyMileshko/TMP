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
import com.tmp.warehouse.domain.TransferTaskAssignment;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseTransferDocument;
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
 * Warehouse operational inbox projection over DRAFT {@code warehouse.transfer} documents (Stage
 * 3.5.5).
 *
 * <p>Task existence is derived from Document Engine lifecycle + Warehouse payload. Worker
 * assignment is informational (not an exclusive lock).
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
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.transferDocuments = Objects.requireNonNull(transferDocuments, "transferDocuments");
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
     * Lists TRANSFER_PREPARATION tasks for the current user's responsible source warehouses.
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

        List<DocumentMetadata> drafts = scanDraftTransferDocuments();
        if (drafts.isEmpty()) {
            return List.of();
        }

        List<UUID> documentIds = drafts.stream().map(DocumentMetadata::id).toList();
        Map<UUID, WarehouseTransferDocument> payloads =
                transferDocuments.findByDocumentIds(documentIds);
        Map<UUID, TransferTaskAssignment> assignments = taskStates.findByDocumentIds(documentIds);
        Map<UUID, Warehouse> warehouseById = warehouseIndex();

        List<WarehouseTaskView> tasks = new ArrayList<>();
        for (DocumentMetadata metadata : drafts) {
            WarehouseTransferDocument payload = payloads.get(metadata.id());
            if (payload == null) {
                continue;
            }
            UUID sourceId = payload.sourceWarehouseId().value();
            if (!responsible.contains(sourceId)) {
                continue;
            }
            tasks.add(
                    toTaskView(
                            metadata,
                            payload,
                            assignments.get(metadata.id()),
                            warehouseById));
        }
        tasks.sort(TASK_ORDER);
        return List.copyOf(tasks);
    }

    /**
     * Informational take-in-work / takeover. Caller must already have enforced RBAC transfer
     * permission; this method enforces authentication, DRAFT transfer existence, and source
     * responsibility.
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
                            if (metadata.status() != DocumentStatus.DRAFT) {
                                throw new IllegalStateException(
                                        "Transfer preparation task exists only for DRAFT: documentId="
                                                + documentId
                                                + ", status="
                                                + metadata.status());
                            }
                            WarehouseTransferDocument payload =
                                    transferDocuments
                                            .findByDocumentId(documentId)
                                            .orElseThrow(
                                                    () ->
                                                            new IllegalArgumentException(
                                                                    "Transfer document payload not found: "
                                                                            + documentId));
                            responsibilityGuard.requireResponsible(payload.sourceWarehouseId());
                            TransferTaskAssignment assignment =
                                    taskStates.takeInWork(documentId, userId, now);
                            return toTaskView(
                                    metadata, payload, assignment, warehouseIndex());
                        });
        if (view == null) {
            throw new IllegalStateException("takeTransferTaskInWork returned null");
        }
        return view;
    }

    private List<DocumentMetadata> scanDraftTransferDocuments() {
        List<DocumentMetadata> all = new ArrayList<>();
        int page = 0;
        while (true) {
            int offset = page * DOCUMENT_SCAN_PAGE_SIZE;
            List<DocumentMetadata> batch =
                    documentEngine.search(
                            new DocumentQuery(
                                    Optional.of(
                                            WarehouseTransferDocumentProcessor.DOCUMENT_TYPE_ID),
                                    Optional.of(DocumentStatus.DRAFT),
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
            TransferTaskAssignment assignment,
            Map<UUID, Warehouse> warehouseById) {
        Warehouse source = warehouseById.get(payload.sourceWarehouseId().value());
        Warehouse destination = warehouseById.get(payload.destinationWarehouseId().value());
        WarehouseTaskState state =
                assignment == null ? WarehouseTaskState.NEW : WarehouseTaskState.IN_WORK;
        return new WarehouseTaskView(
                metadata.id(),
                metadata.documentNumber(),
                WarehouseTaskKind.TRANSFER_PREPARATION,
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
                metadata.createdAt());
    }

    private static int taskStateRank(WarehouseTaskState state) {
        return switch (state) {
            case NEW -> 0;
            case IN_WORK -> 1;
        };
    }
}
