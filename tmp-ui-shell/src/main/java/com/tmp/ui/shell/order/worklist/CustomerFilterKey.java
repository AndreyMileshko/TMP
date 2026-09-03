package com.tmp.ui.shell.order.worklist;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Persistable identity of an Orders customer-filter selection. Distinct from Order domain
 * customer master data: supports stable refs, legacy names, and truly unassigned Orders.
 */
public final class CustomerFilterKey {

    public enum Kind {
        REF,
        NAME,
        UNASSIGNED
    }

    private static final String UNASSIGNED_TOKEN = "UNASSIGNED";
    private static final String REF_PREFIX = "REF:";
    private static final String NAME_PREFIX = "NAME:";

    private final Kind kind;
    private final String value;

    private CustomerFilterKey(Kind kind, String value) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.value = value;
    }

    public static CustomerFilterKey ref(String customerRef) {
        String normalized = requireNonBlank(customerRef, "customerRef");
        return new CustomerFilterKey(Kind.REF, normalized);
    }

    public static CustomerFilterKey name(String customerName) {
        String normalized = normalizeName(customerName);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("customerName must be non-blank");
        }
        return new CustomerFilterKey(Kind.NAME, normalized);
    }

    public static CustomerFilterKey unassigned() {
        return new CustomerFilterKey(Kind.UNASSIGNED, null);
    }

    public Kind kind() {
        return kind;
    }

    public String value() {
        return value;
    }

    public boolean isRef() {
        return kind == Kind.REF;
    }

    public boolean isName() {
        return kind == Kind.NAME;
    }

    public boolean isUnassigned() {
        return kind == Kind.UNASSIGNED;
    }

    public String serialize() {
        return switch (kind) {
            case REF -> REF_PREFIX + value;
            case NAME -> NAME_PREFIX + value;
            case UNASSIGNED -> UNASSIGNED_TOKEN;
        };
    }

    /**
     * Parses a versioned token. Unknown/corrupt tokens are empty (fail-safe ignore).
     */
    public static Optional<CustomerFilterKey> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String trimmed = token.trim();
        if (UNASSIGNED_TOKEN.equals(trimmed)) {
            return Optional.of(unassigned());
        }
        if (trimmed.startsWith(REF_PREFIX)) {
            String raw = trimmed.substring(REF_PREFIX.length()).trim();
            if (raw.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(ref(raw));
        }
        if (trimmed.startsWith(NAME_PREFIX)) {
            String raw = trimmed.substring(NAME_PREFIX.length());
            String normalized = normalizeName(raw);
            if (normalized.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(name(normalized));
        }
        return Optional.empty();
    }

    public static String normalizeName(String customerName) {
        return customerName == null ? "" : customerName.trim();
    }

    public static boolean isBlankName(String customerName) {
        return normalizeName(customerName).isEmpty();
    }

    public static Parts parts(Set<CustomerFilterKey> keys) {
        Set<String> refs = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        boolean unassigned = false;
        if (keys != null) {
            for (CustomerFilterKey key : keys) {
                if (key == null) {
                    continue;
                }
                if (key.isRef()) {
                    refs.add(key.value());
                } else if (key.isName()) {
                    names.add(key.value());
                } else if (key.isUnassigned()) {
                    unassigned = true;
                }
            }
        }
        return new Parts(Set.copyOf(refs), Set.copyOf(names), unassigned);
    }

    public static Set<CustomerFilterKey> fromLegacyRefs(Set<String> customerRefs, boolean includeUnassigned) {
        Set<CustomerFilterKey> keys = new LinkedHashSet<>();
        if (customerRefs != null) {
            for (String ref : customerRefs) {
                if (ref == null || ref.isBlank()) {
                    continue;
                }
                keys.add(ref(ref.trim()));
            }
        }
        if (includeUnassigned) {
            keys.add(unassigned());
        }
        return Set.copyOf(keys);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CustomerFilterKey that)) {
            return false;
        }
        return kind == that.kind && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value);
    }

    @Override
    public String toString() {
        return serialize();
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return value.trim();
    }

    public record Parts(Set<String> refs, Set<String> names, boolean unassigned) {}
}
