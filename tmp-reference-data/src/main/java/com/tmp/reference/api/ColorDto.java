package com.tmp.reference.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Public immutable view of a TMP catalog color (TMP-SPEC-015 section 17).
 *
 * <p>Does not expose persistence entities or mutable domain internals.
 */
public final class ColorDto {

    /** Active-status filter for {@link ColorCatalogQueryApi#listColors}. */
    public enum ActiveFilter {
        ACTIVE,
        ARCHIVED,
        ALL
    }

    private final UUID id;
    private final String code;
    private final boolean active;
    private final long version;

    public ColorDto(UUID id, String code, boolean active, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = Objects.requireNonNull(code, "code");
        this.active = active;
        this.version = version;
    }

    public UUID id() {
        return id;
    }

    public String code() {
        return code;
    }

    public boolean active() {
        return active;
    }

    public long version() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ColorDto that)) {
            return false;
        }
        return active == that.active
                && version == that.version
                && id.equals(that.id)
                && code.equals(that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, code, active, version);
    }

    @Override
    public String toString() {
        return "ColorDto{id=" + id + ", code=" + code + ", active=" + active + ", version=" + version + "}";
    }
}
