package com.tmp.order.application.payload;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.domain.OrderedQuantity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Typed payload of {@code ORDER_ITEM_REVISION_UPDATE} (Specification §6.3 / §13.2).
 *
 * <p>Addresses only an existing Draft Revision ({@link RevisionStatus#DRAFT}). Changing an
 * approved Revision via this payload is impossible — construction requires Draft target status.
 * Specification lines are an immutable typed collection. Immutable; identity is bound to
 * {@link DocumentTypeCode#ORDER_ITEM_REVISION_UPDATE}.
 */
public final class OrderItemRevisionUpdatePayload implements OrderDocumentPayload {

    private final PayloadIdentity identity;
    private final OrderItemId orderItemId;
    private final RevisionNumber revisionNumber;
    private final RevisionStatus targetRevisionStatus;
    private final OrderedQuantity orderedQuantity;
    private final List<OrderItemRevisionPayloadLine> lines;

    private OrderItemRevisionUpdatePayload(
            PayloadIdentity identity,
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            RevisionStatus targetRevisionStatus,
            OrderedQuantity orderedQuantity,
            List<OrderItemRevisionPayloadLine> lines) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.revisionNumber = Objects.requireNonNull(revisionNumber, "revisionNumber");
        this.targetRevisionStatus =
                Objects.requireNonNull(targetRevisionStatus, "targetRevisionStatus");
        this.orderedQuantity = Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        Objects.requireNonNull(lines, "lines");
        if (identity.documentTypeCode() != DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE) {
            throw new IllegalArgumentException(
                    "OrderItemRevisionUpdatePayload requires document type "
                            + "ORDER_ITEM_REVISION_UPDATE, got "
                            + identity.documentTypeCode());
        }
        if (targetRevisionStatus != RevisionStatus.DRAFT) {
            throw new IllegalArgumentException(
                    "ORDER_ITEM_REVISION_UPDATE may address only a Draft Revision; got "
                            + targetRevisionStatus);
        }
        this.lines = List.copyOf(lines);
    }

    public static OrderItemRevisionUpdatePayload create(
            DocumentId documentId,
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            OrderedQuantity orderedQuantity,
            List<OrderItemRevisionPayloadLine> lines,
            Instant now) {
        Objects.requireNonNull(now, "now");
        return new OrderItemRevisionUpdatePayload(
                PayloadIdentity.initialDraft(
                        documentId, DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE, now),
                orderItemId,
                revisionNumber,
                RevisionStatus.DRAFT,
                orderedQuantity,
                lines);
    }

    public static OrderItemRevisionUpdatePayload rehydrate(
            PayloadIdentity identity,
            OrderItemId orderItemId,
            RevisionNumber revisionNumber,
            RevisionStatus targetRevisionStatus,
            OrderedQuantity orderedQuantity,
            List<OrderItemRevisionPayloadLine> lines) {
        return new OrderItemRevisionUpdatePayload(
                identity,
                orderItemId,
                revisionNumber,
                targetRevisionStatus,
                orderedQuantity,
                lines);
    }

    public OrderItemRevisionUpdatePayload withContent(
            OrderedQuantity newQuantity,
            List<OrderItemRevisionPayloadLine> newLines,
            Instant updatedAt) {
        Objects.requireNonNull(newQuantity, "newQuantity");
        Objects.requireNonNull(newLines, "newLines");
        return new OrderItemRevisionUpdatePayload(
                identity.withNextRevision(updatedAt),
                orderItemId,
                revisionNumber,
                RevisionStatus.DRAFT,
                newQuantity,
                newLines);
    }

    @Override
    public PayloadIdentity identity() {
        return identity;
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public RevisionNumber revisionNumber() {
        return revisionNumber;
    }

    /**
     * Always {@link RevisionStatus#DRAFT} — update payloads cannot target an approved Revision.
     */
    public RevisionStatus targetRevisionStatus() {
        return targetRevisionStatus;
    }

    public OrderedQuantity orderedQuantity() {
        return orderedQuantity;
    }

    /**
     * Immutable view of specification lines. The returned list rejects structural modification.
     */
    public List<OrderItemRevisionPayloadLine> lines() {
        return lines;
    }

    /**
     * Defensive copy of lines for callers that need a mutable working set without affecting this
     * payload.
     */
    public List<OrderItemRevisionPayloadLine> copyLines() {
        return new ArrayList<>(lines);
    }

    public static List<OrderItemRevisionPayloadLine> unmodifiableLines(
            List<OrderItemRevisionPayloadLine> source) {
        return Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(source, "source")));
    }
}
