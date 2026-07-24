package com.tmp.order.domain;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.RevisionNumber;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Order Item aggregate root (Specification §5.2 / §6 / §9).
 *
 * <p>Owns its revisions and their specifications in a single transactional boundary. Commercial
 * lifecycle: {@code DRAFT → ACTIVE}, {@code DRAFT → CANCELLED}. Stage 5 forbids
 * {@code ACTIVE → CANCELLED}. Does not store Production Status or production quantities.
 */
public final class OrderItem {

    private final OrderItemId id;
    private final OrderId orderId;
    private final ItemCommercialData commercialData;
    private final OrderItemStatus status;
    private final RevisionNumber activeRevisionNumber;
    private final RevisionNumber draftRevisionNumber;
    private final Map<RevisionNumber, OrderItemRevision> revisions;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private OrderItem(
            OrderItemId id,
            OrderId orderId,
            ItemCommercialData commercialData,
            OrderItemStatus status,
            RevisionNumber activeRevisionNumber,
            RevisionNumber draftRevisionNumber,
            Map<RevisionNumber, OrderItemRevision> revisions,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.commercialData = Objects.requireNonNull(commercialData, "commercialData");
        this.status = Objects.requireNonNull(status, "status");
        this.activeRevisionNumber = activeRevisionNumber;
        this.draftRevisionNumber = draftRevisionNumber;
        this.revisions = Collections.unmodifiableMap(new LinkedHashMap<>(revisions));
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Creates a new order item in {@link OrderItemStatus#DRAFT} with draft revision {@code 1}.
     */
    public static OrderItem create(
            OrderItemId id,
            OrderId orderId,
            ItemCommercialData commercialData,
            OrderedQuantity initialQuantity,
            Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(initialQuantity, "initialQuantity");
        Instant now = clock.instant();
        RevisionNumber first = RevisionNumber.first();
        OrderItemRevision revision =
                OrderItemRevision.createDraft(id, first, initialQuantity, null);
        Map<RevisionNumber, OrderItemRevision> revisions = new LinkedHashMap<>();
        revisions.put(first, revision);
        return new OrderItem(
                id,
                orderId,
                commercialData,
                OrderItemStatus.DRAFT,
                null,
                first,
                revisions,
                0L,
                now,
                now);
    }

    /**
     * Rehydrates a persisted order item. Used by persistence adapters only.
     */
    public static OrderItem rehydrate(
            OrderItemId id,
            OrderId orderId,
            ItemCommercialData commercialData,
            OrderItemStatus status,
            RevisionNumber activeRevisionNumber,
            RevisionNumber draftRevisionNumber,
            Map<RevisionNumber, OrderItemRevision> revisions,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        return new OrderItem(
                id,
                orderId,
                commercialData,
                status,
                activeRevisionNumber,
                draftRevisionNumber,
                revisions,
                version,
                createdAt,
                updatedAt);
    }

    public OrderItem updateCommercialData(ItemCommercialData newData, Clock clock) {
        Objects.requireNonNull(newData, "newData");
        Objects.requireNonNull(clock, "clock");
        requireDraftStatus("update commercial data");
        return copy(newData, status, activeRevisionNumber, draftRevisionNumber, revisions, clock);
    }

    /**
     * Cancels the item: {@code DRAFT → CANCELLED}. Stage 5 forbids {@code ACTIVE → CANCELLED}.
     */
    public OrderItem cancel(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        if (status == OrderItemStatus.ACTIVE) {
            throw new InvalidOrderStateException(
                    "Active order item cannot be cancelled in Stage 5: " + id);
        }
        if (status == OrderItemStatus.CANCELLED) {
            throw new InvalidOrderStateException("Order item already cancelled: " + id);
        }
        if (status != OrderItemStatus.DRAFT) {
            throw new InvalidOrderStateException(
                    "Order item can be cancelled only from DRAFT, current=" + status + ", id=" + id);
        }
        return copy(
                commercialData,
                OrderItemStatus.CANCELLED,
                activeRevisionNumber,
                draftRevisionNumber,
                revisions,
                clock);
    }

    /**
     * Creates the next draft revision ({@code N+1}) for an active item. Does not change the active
     * revision. Requires absence of an existing draft.
     */
    public OrderItem createNextDraftRevision(OrderedQuantity quantity, Clock clock) {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(clock, "clock");
        requireNotCancelled("create draft revision");
        if (status != OrderItemStatus.ACTIVE) {
            throw new InvalidOrderStateException(
                    "Next draft revision can be created only for ACTIVE item: " + id);
        }
        if (draftRevisionNumber != null) {
            throw new InvalidOrderStateException(
                    "Order item already has draft revision " + draftRevisionNumber + ": " + id);
        }
        if (activeRevisionNumber == null) {
            throw new InvalidOrderStateException(
                    "Cannot create next revision without an active revision: " + id);
        }
        RevisionNumber next = activeRevisionNumber.next();
        if (revisions.containsKey(next)) {
            throw new InvalidOrderStateException(
                    "Revision number already used: " + next + " on item " + id);
        }
        OrderItemRevision draft =
                OrderItemRevision.createDraft(id, next, quantity, activeRevisionNumber);
        Map<RevisionNumber, OrderItemRevision> nextRevisions = new LinkedHashMap<>(revisions);
        nextRevisions.put(next, draft);
        return copy(commercialData, status, activeRevisionNumber, next, nextRevisions, clock);
    }

    /**
     * Updates ordered quantity of the current draft revision.
     */
    public OrderItem updateDraftOrderedQuantity(OrderedQuantity quantity, Clock clock) {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(clock, "clock");
        OrderItemRevision draft = requireDraftRevision();
        OrderItemRevision updated = draft.withOrderedQuantity(quantity);
        return replaceRevision(updated, clock);
    }

    /**
     * Replaces the draft revision's specification (STAGE5-007 editing entry point).
     */
    public OrderItem updateDraftSpecification(ItemSpecification specification, Clock clock) {
        Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(clock, "clock");
        OrderItemRevision draft = requireDraftRevision();
        OrderItemRevision updated = draft.withSpecification(specification);
        return replaceRevision(updated, clock);
    }

    /**
     * Approves the current draft revision atomically: marks it {@code APPROVED} (immutable),
     * assigns it as the new active revision, clears the draft pointer, preserves previous active
     * revisions, and transitions the item {@code DRAFT → ACTIVE} on first approval.
     */
    public OrderItem approveDraftRevision(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        requireNotCancelled("approve draft revision");
        if (draftRevisionNumber == null) {
            throw new InvalidOrderStateException(
                    "Cannot approve: no draft revision on item " + id);
        }
        OrderItemRevision draft = revisions.get(draftRevisionNumber);
        if (draft == null) {
            throw new InvalidOrderStateException(
                    "Draft revision " + draftRevisionNumber + " missing on item " + id);
        }
        if (!draft.isDraft()) {
            throw new InvalidOrderStateException(
                    "Cannot re-approve revision " + draftRevisionNumber + " on item " + id);
        }
        if (draft.specification().isEmpty() || draft.specification().orElseThrow().isEmpty()) {
            throw new InvalidOrderStateException(
                    "Cannot approve revision without a non-empty specification: "
                            + id + "/" + draftRevisionNumber);
        }
        RevisionNumber previousActive = activeRevisionNumber;
        OrderItemRevision approved = draft.approved();
        Map<RevisionNumber, OrderItemRevision> nextRevisions = new LinkedHashMap<>(revisions);
        nextRevisions.put(approved.revisionNumber(), approved);
        if (previousActive != null) {
            OrderItemRevision previous = nextRevisions.get(previousActive);
            if (previous != null && !previous.isApproved()) {
                throw new InvalidOrderStateException(
                        "Previous active revision must already be approved: " + previousActive);
            }
        }
        OrderItemStatus nextStatus =
                status == OrderItemStatus.DRAFT ? OrderItemStatus.ACTIVE : status;
        if (status == OrderItemStatus.ACTIVE && nextStatus != OrderItemStatus.ACTIVE) {
            throw new InvalidOrderStateException("Unexpected status after approval: " + id);
        }
        return new OrderItem(
                id,
                orderId,
                commercialData,
                nextStatus,
                approved.revisionNumber(),
                null,
                nextRevisions,
                version,
                createdAt,
                clock.instant());
    }

    private OrderItem replaceRevision(OrderItemRevision revision, Clock clock) {
        Map<RevisionNumber, OrderItemRevision> nextRevisions = new LinkedHashMap<>(revisions);
        nextRevisions.put(revision.revisionNumber(), revision);
        return copy(
                commercialData,
                status,
                activeRevisionNumber,
                draftRevisionNumber,
                nextRevisions,
                clock);
    }

    private OrderItemRevision requireDraftRevision() {
        requireNotCancelled("edit draft revision");
        if (draftRevisionNumber == null) {
            throw new InvalidOrderStateException("No draft revision on item " + id);
        }
        OrderItemRevision draft = revisions.get(draftRevisionNumber);
        if (draft == null || !draft.isDraft()) {
            throw new InvalidOrderStateException(
                    "Draft revision " + draftRevisionNumber + " is not editable on item " + id);
        }
        return draft;
    }

    private OrderItem copy(
            ItemCommercialData data,
            OrderItemStatus newStatus,
            RevisionNumber active,
            RevisionNumber draft,
            Map<RevisionNumber, OrderItemRevision> nextRevisions,
            Clock clock) {
        return new OrderItem(
                id,
                orderId,
                data,
                newStatus,
                active,
                draft,
                nextRevisions,
                version,
                createdAt,
                clock.instant());
    }

    private void requireDraftStatus(String action) {
        if (status != OrderItemStatus.DRAFT) {
            throw new InvalidOrderStateException(
                    "Cannot " + action + " unless item is DRAFT, current=" + status + ", id=" + id);
        }
    }

    private void requireNotCancelled(String action) {
        if (status == OrderItemStatus.CANCELLED) {
            throw new InvalidOrderStateException(
                    "Cannot " + action + " on cancelled item: " + id);
        }
    }

    public OrderItemId id() {
        return id;
    }

    public OrderId orderId() {
        return orderId;
    }

    public ItemCommercialData commercialData() {
        return commercialData;
    }

    public OrderItemStatus status() {
        return status;
    }

    public Optional<RevisionNumber> activeRevisionNumber() {
        return Optional.ofNullable(activeRevisionNumber);
    }

    public Optional<RevisionNumber> draftRevisionNumber() {
        return Optional.ofNullable(draftRevisionNumber);
    }

    public Optional<OrderItemRevision> revision(RevisionNumber number) {
        return Optional.ofNullable(revisions.get(number));
    }

    public Optional<OrderItemRevision> activeRevision() {
        return activeRevisionNumber == null
                ? Optional.empty()
                : Optional.ofNullable(revisions.get(activeRevisionNumber));
    }

    public Optional<OrderItemRevision> draftRevision() {
        return draftRevisionNumber == null
                ? Optional.empty()
                : Optional.ofNullable(revisions.get(draftRevisionNumber));
    }

    /** Unmodifiable view of all revisions keyed by number. */
    public Map<RevisionNumber, OrderItemRevision> revisions() {
        return revisions;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public boolean isDraft() {
        return status == OrderItemStatus.DRAFT;
    }

    public boolean isActive() {
        return status == OrderItemStatus.ACTIVE;
    }

    public boolean isCancelled() {
        return status == OrderItemStatus.CANCELLED;
    }
}
