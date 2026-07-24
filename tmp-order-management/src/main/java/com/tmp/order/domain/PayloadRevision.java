package com.tmp.order.domain;

/**
 * Optimistic-lock revision counter of a draft document payload (Specification §11.2/§11.3).
 *
 * <p>Internal to Order Management. Starts at {@code 0} for a freshly created draft payload and is
 * incremented on every successful draft update. Once the platform document is being posted the
 * payload becomes immutable and its revision no longer changes.
 */
public final class PayloadRevision {

    private static final long INITIAL_VALUE = 0L;

    private final long value;

    private PayloadRevision(long value) {
        this.value = value;
    }

    /**
     * Wraps an existing revision counter.
     *
     * @param value revision counter, must be {@code >= 0}
     * @return the payload revision
     * @throws IllegalArgumentException if {@code value < 0}
     */
    public static PayloadRevision of(long value) {
        if (value < INITIAL_VALUE) {
            throw new IllegalArgumentException(
                    "Payload revision must be >= " + INITIAL_VALUE + ": " + value);
        }
        return new PayloadRevision(value);
    }

    /**
     * Returns the initial revision counter ({@code 0}) for a new draft payload.
     *
     * @return the initial payload revision
     */
    public static PayloadRevision initial() {
        return new PayloadRevision(INITIAL_VALUE);
    }

    /**
     * Returns the next revision counter after a successful draft update.
     *
     * @return the incremented payload revision
     */
    public PayloadRevision next() {
        return new PayloadRevision(value + 1);
    }

    public long value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PayloadRevision that)) {
            return false;
        }
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
