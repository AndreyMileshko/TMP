package com.tmp.order.api;

/**
 * Immutable, type-safe number of an order item revision.
 *
 * <p>Revision numbers start at {@code 1}, increase monotonically within an order item and are
 * never reused (Specification §5.2/§5.3). This value object enforces the {@code >= 1} invariant
 * and the monotonic successor rule; it carries no revision content.
 */
public final class RevisionNumber implements Comparable<RevisionNumber> {

    private static final int FIRST_VALUE = 1;

    private final int value;

    private RevisionNumber(int value) {
        this.value = value;
    }

    /**
     * Wraps an existing revision number.
     *
     * @param value revision number, must be {@code >= 1}
     * @return the revision number
     * @throws IllegalArgumentException if {@code value < 1}
     */
    public static RevisionNumber of(int value) {
        if (value < FIRST_VALUE) {
            throw new IllegalArgumentException(
                    "Revision number must be >= " + FIRST_VALUE + ": " + value);
        }
        return new RevisionNumber(value);
    }

    /**
     * Returns the first revision number ({@code 1}).
     *
     * @return the first revision number
     */
    public static RevisionNumber first() {
        return new RevisionNumber(FIRST_VALUE);
    }

    /**
     * Returns the next (monotonically increasing) revision number.
     *
     * @return the successor revision number
     */
    public RevisionNumber next() {
        return new RevisionNumber(value + 1);
    }

    public int value() {
        return value;
    }

    /**
     * Tells whether this revision number is strictly greater than the given one.
     *
     * @param other the revision number to compare against
     * @return {@code true} if this number is after {@code other}
     */
    public boolean isAfter(RevisionNumber other) {
        return value > other.value;
    }

    @Override
    public int compareTo(RevisionNumber other) {
        return Integer.compare(value, other.value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RevisionNumber that)) {
            return false;
        }
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
