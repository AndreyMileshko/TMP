package com.tmp.ui.shell.order.worklist;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic comparators for the operational Orders list. Applied to the full matched set
 * before pagination. Default: {@link OrderListSortField#CREATED_AT} DESC (newest first).
 */
public final class OrderOperationalListSorter {

    private static final Map<OrderOperationalStatus, Integer> STATUS_RANK =
            new EnumMap<>(OrderOperationalStatus.class);

    static {
        STATUS_RANK.put(OrderOperationalStatus.EDITING, 0);
        STATUS_RANK.put(OrderOperationalStatus.AWAITING_PRODUCTION, 1);
        STATUS_RANK.put(OrderOperationalStatus.IN_PRODUCTION, 2);
        STATUS_RANK.put(OrderOperationalStatus.PARTIALLY_COMPLETED, 3);
        STATUS_RANK.put(OrderOperationalStatus.COMPLETED, 4);
        STATUS_RANK.put(OrderOperationalStatus.CANCELLED, 5);
        STATUS_RANK.put(OrderOperationalStatus.STATUS_UNAVAILABLE, 6);
    }

    private OrderOperationalListSorter() {}

    public static OrderListSortField defaultField() {
        return OrderListSortField.CREATED_AT;
    }

    public static OrderListSortDirection defaultDirection() {
        return OrderListSortDirection.DESC;
    }

    public static int statusBusinessRank(OrderOperationalStatus status) {
        Objects.requireNonNull(status, "status");
        Integer rank = STATUS_RANK.get(status);
        return rank == null ? Integer.MAX_VALUE : rank;
    }

    public static Comparator<OrderOperationalSummary> comparator(
            OrderListSortField field, OrderListSortDirection direction) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(direction, "direction");
        Comparator<OrderOperationalSummary> primary =
                switch (field) {
                    case ORDER_NUMBER -> Comparator.comparing(
                            OrderOperationalSummary::orderNumber, OrderOperationalListSorter::compareNatural);
                    case CUSTOMER -> customerComparator(direction);
                    case CREATED_AT -> Comparator.comparing(OrderOperationalSummary::createdAt);
                    case ITEM_COUNT -> Comparator.comparingLong(OrderOperationalSummary::itemQuantity);
                    case STATUS -> Comparator.comparingInt(
                            row -> statusBusinessRank(row.operationalStatus()));
                };
        // CUSTOMER applies ASC/DESC only to non-empty names; null/blank stay last (do not reverse).
        if (field != OrderListSortField.CUSTOMER && direction == OrderListSortDirection.DESC) {
            primary = primary.reversed();
        }
        Comparator<OrderOperationalSummary> byCreatedAt = Comparator.comparing(OrderOperationalSummary::createdAt);
        Comparator<OrderOperationalSummary> byOrderId =
                Comparator.comparing(row -> row.orderId().value());
        if (field == OrderListSortField.CREATED_AT) {
            // Primary already createdAt; stable secondary by orderId in the same direction.
            return direction == OrderListSortDirection.DESC
                    ? primary.thenComparing(byOrderId.reversed())
                    : primary.thenComparing(byOrderId);
        }
        // Secondary: createdAt DESC (newest first among ties), then orderId for full stability.
        return primary.thenComparing(byCreatedAt.reversed()).thenComparing(byOrderId);
    }

    public static void sortInPlace(
            List<OrderOperationalSummary> rows, OrderListSortField field, OrderListSortDirection direction) {
        Objects.requireNonNull(rows, "rows");
        rows.sort(comparator(field, direction));
    }

    /**
     * Natural / alphanumeric comparison: numeric runs compared as integers, otherwise
     * case-insensitive text. Deterministic for mixed tokens such as {@code TEST-001} / {@code 10}.
     */
    static int compareNatural(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startA = i;
                int startB = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                String numA = a.substring(startA, i).replaceFirst("^0+(?!$)", "");
                String numB = b.substring(startB, j).replaceFirst("^0+(?!$)", "");
                if (numA.length() != numB.length()) {
                    return Integer.compare(numA.length(), numB.length());
                }
                int cmp = numA.compareTo(numB);
                if (cmp != 0) {
                    return cmp;
                }
            } else {
                int cmp = Character.compare(
                        Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (cmp != 0) {
                    return cmp;
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    /**
     * Customer sort: direction applies only to non-empty names; null/blank always last in ASC and
     * DESC. Uses the same display source as the Заказчик column (raw name; whitespace = missing).
     */
    private static Comparator<OrderOperationalSummary> customerComparator(OrderListSortDirection direction) {
        Comparator<String> names = String.CASE_INSENSITIVE_ORDER;
        if (direction == OrderListSortDirection.DESC) {
            names = names.reversed();
        }
        return Comparator.comparing(OrderOperationalListSorter::customerSortKey, Comparator.nullsLast(names));
    }

    private static String customerSortKey(OrderOperationalSummary row) {
        String name = row.customerName();
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
