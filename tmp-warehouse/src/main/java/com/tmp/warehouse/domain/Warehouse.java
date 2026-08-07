package com.tmp.warehouse.domain;

import java.util.Objects;

/**
 * Physical warehouse (Warehouse Specification §1 / §5).
 *
 * <p>May be inactive. Code is mandatory.
 */
public final class Warehouse {

    private final WarehouseId id;
    private final String code;
    private final String name;
    private final boolean active;

    private Warehouse(WarehouseId id, String code, String name, boolean active) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.active = active;
    }

    public static Warehouse create(WarehouseId id, String code, String name) {
        return of(id, code, name, true);
    }

    public static Warehouse of(WarehouseId id, String code, String name, boolean active) {
        Objects.requireNonNull(id, "id");
        String normalizedCode = requireNonBlank(code, "code");
        String normalizedName = requireNonBlank(name, "name");
        return new Warehouse(id, normalizedCode, normalizedName, active);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    public Warehouse deactivate() {
        if (!active) {
            return this;
        }
        return new Warehouse(id, code, name, false);
    }

    public Warehouse activate() {
        if (active) {
            return this;
        }
        return new Warehouse(id, code, name, true);
    }

    public WarehouseId id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public boolean active() {
        return active;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Warehouse that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Warehouse{id=" + id + ", code=" + code + ", active=" + active + '}';
    }
}
