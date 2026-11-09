package com.tmp.reference.domain;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for the TMP color catalog ({@code reference.colors}).
 *
 * <p>Does not expose delete or code-change operations (TMP-SPEC-015 section 5.3).
 */
public interface ColorRepository {

    /**
     * Inserts a new color. Rejects case-insensitive duplicates via {@link
     * Color.DuplicateCodeException}.
     */
    Color insert(Color color);

    /**
     * Persists archive / restore (and audit) changes with optimistic locking. Never updates {@code
     * code} / {@code code_key}.
     */
    Color update(Color color);

    Optional<Color> findById(ColorId id);

    /** Exact match on preserved {@code code} spelling. */
    Optional<Color> findByCode(String code);

    /** Case-insensitive lookup via normalized {@code code_key}. */
    Optional<Color> findByCodeKey(ColorCodeKey codeKey);

    /**
     * Lists colors with optional active filter and case-insensitive substring search on {@code
     * code}.
     *
     * @param activeFilter {@code null} = all; {@code true}/{@code false} = filter by {@code active}
     * @param searchText blank or {@code null} = no text filter; otherwise substring of {@code code}
     */
    List<Color> search(Boolean activeFilter, String searchText);
}
