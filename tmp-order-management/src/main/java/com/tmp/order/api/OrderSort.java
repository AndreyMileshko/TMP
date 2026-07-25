package com.tmp.order.api;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Sort specification for Public Query API order listings (Specification §15.1.2).
 *
 * <p>Allowed fields: {@code createdAt}, {@code orderId}, {@code orderNumber}, {@code status}.
 * Default: {@code createdAt DESC}, {@code orderId DESC}.
 */
public final class OrderSort {

    public enum Field {
        CREATED_AT("createdAt"),
        ORDER_ID("orderId"),
        ORDER_NUMBER("orderNumber"),
        STATUS("status");

        private final String apiName;

        Field(String apiName) {
            this.apiName = apiName;
        }

        public String apiName() {
            return apiName;
        }

        public static Field fromApiName(String name) {
            Objects.requireNonNull(name, "name");
            String normalized = name.trim();
            for (Field field : values()) {
                if (field.apiName.equalsIgnoreCase(normalized)) {
                    return field;
                }
            }
            throw new IllegalArgumentException(
                    "Unknown sort field '" + name + "'; allowed: createdAt, orderId, orderNumber, status");
        }
    }

    public enum Direction {
        ASC,
        DESC;

        public static Direction fromApiName(String name) {
            Objects.requireNonNull(name, "name");
            try {
                return Direction.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Unknown sort direction '" + name + "'; allowed: ASC, DESC", ex);
            }
        }
    }

    public record Order(Field field, Direction direction) {
        public Order {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(direction, "direction");
        }
    }

    private final List<Order> orders;

    private OrderSort(List<Order> orders) {
        this.orders = List.copyOf(orders);
    }

    /** Default sort: {@code createdAt DESC}, {@code orderId DESC}. */
    public static OrderSort defaultSort() {
        return new OrderSort(List.of(
                new Order(Field.CREATED_AT, Direction.DESC),
                new Order(Field.ORDER_ID, Direction.DESC)));
    }

    /**
     * Creates a sort from one or more field/direction pairs.
     *
     * @throws IllegalArgumentException if no orders are provided
     */
    public static OrderSort of(Order first, Order... rest) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(rest, "rest");
        java.util.ArrayList<Order> all = new java.util.ArrayList<>();
        all.add(first);
        for (Order order : rest) {
            Objects.requireNonNull(order, "order");
            all.add(order);
        }
        return new OrderSort(all);
    }

    /**
     * Parses a single {@code field} / {@code direction} pair from API names.
     *
     * @throws IllegalArgumentException if field or direction is unknown
     */
    public static OrderSort of(String fieldName, String directionName) {
        return of(new Order(Field.fromApiName(fieldName), Direction.fromApiName(directionName)));
    }

    public List<Order> orders() {
        return orders;
    }
}
