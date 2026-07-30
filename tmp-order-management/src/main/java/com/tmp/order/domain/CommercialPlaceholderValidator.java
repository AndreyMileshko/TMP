package com.tmp.order.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Rejects placeholder commercial values prohibited by ADR-030 (UNKNOWN, N/A, IMPORT, em-dash stubs).
 */
final class CommercialPlaceholderValidator {

    private static final Set<String> FORBIDDEN_PLACEHOLDERS =
            Set.of("UNKNOWN", "N/A", "NA", "IMPORT", "—", "-", "NONE", "TBD");

    private CommercialPlaceholderValidator() {}

    static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String requireNonPlaceholder(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        rejectPlaceholder(trimmed, field);
        return trimmed;
    }

    static void rejectPlaceholderIfPresent(String value, String field) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        rejectPlaceholder(trimmed, field);
    }

    private static void rejectPlaceholder(String trimmed, String field) {
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (FORBIDDEN_PLACEHOLDERS.contains(upper)) {
            throw new IllegalArgumentException(
                    field + " must not use placeholder value: " + trimmed);
        }
    }
}
