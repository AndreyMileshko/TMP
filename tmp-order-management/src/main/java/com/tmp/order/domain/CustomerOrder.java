package com.tmp.order.domain;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Customer Order aggregate root (Specification §5.1 / §8).
 *
 * <p>Commercial lifecycle in Stage 5: {@code DRAFT → APPROVED}, {@code DRAFT → CANCELLED}.
 * {@code APPROVED → CANCELLED}, transitions out of {@code CANCELLED}, and re-approval are forbidden.
 * Commercial fields may change only while {@code DRAFT}. State is immutable from outside; all
 * changes go through aggregate methods that return a new instance.
 *
 * <p>The application / document-processor layer is responsible for the document precondition
 * «≥ 1 active item» before calling {@link #approve(Clock)}; the order aggregate boundary does not
 * contain Order Items (separate aggregate).
 */
public final class CustomerOrder {

    private final OrderId id;
    private final OrderNumber orderNumber;
    private final OrderCommercialData commercialData;
    private final OrderStatus status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private CustomerOrder(
            OrderId id,
            OrderNumber orderNumber,
            OrderCommercialData commercialData,
            OrderStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderNumber = Objects.requireNonNull(orderNumber, "orderNumber");
        this.commercialData = Objects.requireNonNull(commercialData, "commercialData");
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Creates a new customer order in {@link OrderStatus#DRAFT}.
     */
    public static CustomerOrder create(
            OrderId id,
            OrderNumber orderNumber,
            OrderCommercialData commercialData,
            Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Instant now = clock.instant();
        return new CustomerOrder(id, orderNumber, commercialData, OrderStatus.DRAFT, 0L, now, now);
    }

    /**
     * Rehydrates a persisted order. Used by persistence adapters only.
     */
    public static CustomerOrder rehydrate(
            OrderId id,
            OrderNumber orderNumber,
            OrderCommercialData commercialData,
            OrderStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        return new CustomerOrder(
                id, orderNumber, commercialData, status, version, createdAt, updatedAt);
    }

    /**
     * Updates commercial header fields. Allowed only in {@link OrderStatus#DRAFT}.
     */
    public CustomerOrder updateCommercialData(OrderCommercialData newData, Clock clock) {
        Objects.requireNonNull(newData, "newData");
        Objects.requireNonNull(clock, "clock");
        requireDraft("update commercial data");
        return new CustomerOrder(
                id, orderNumber, newData, status, version, createdAt, clock.instant());
    }

    /**
     * Approves the order: {@code DRAFT → APPROVED}.
     *
     * @throws InvalidOrderStateException if the order is not in {@code DRAFT}
     */
    public CustomerOrder approve(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        if (status == OrderStatus.APPROVED) {
            throw new InvalidOrderStateException(
                    "Order already approved; re-approval is forbidden: " + id);
        }
        if (status == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException(
                    "Cancelled order cannot be approved: " + id);
        }
        if (status != OrderStatus.DRAFT) {
            throw new InvalidOrderStateException(
                    "Order can be approved only from DRAFT, current=" + status + ", id=" + id);
        }
        return new CustomerOrder(
                id, orderNumber, commercialData, OrderStatus.APPROVED, version, createdAt,
                clock.instant());
    }

    /**
     * Cancels the order: {@code DRAFT → CANCELLED}. Stage 5 forbids {@code APPROVED → CANCELLED}.
     *
     * @throws InvalidOrderStateException if the order is not in {@code DRAFT}
     */
    public CustomerOrder cancel(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        if (status == OrderStatus.APPROVED) {
            throw new InvalidOrderStateException(
                    "Approved order cannot be cancelled in Stage 5: " + id);
        }
        if (status == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException(
                    "Order already cancelled: " + id);
        }
        if (status != OrderStatus.DRAFT) {
            throw new InvalidOrderStateException(
                    "Order can be cancelled only from DRAFT, current=" + status + ", id=" + id);
        }
        return new CustomerOrder(
                id, orderNumber, commercialData, OrderStatus.CANCELLED, version, createdAt,
                clock.instant());
    }

    private void requireDraft(String action) {
        if (status != OrderStatus.DRAFT) {
            throw new InvalidOrderStateException(
                    "Cannot " + action + " unless order is DRAFT, current=" + status + ", id=" + id);
        }
    }

    public OrderId id() {
        return id;
    }

    public OrderNumber orderNumber() {
        return orderNumber;
    }

    public OrderCommercialData commercialData() {
        return commercialData;
    }

    public OrderStatus status() {
        return status;
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
        return status == OrderStatus.DRAFT;
    }

    public boolean isApproved() {
        return status == OrderStatus.APPROVED;
    }

    public boolean isCancelled() {
        return status == OrderStatus.CANCELLED;
    }
}
