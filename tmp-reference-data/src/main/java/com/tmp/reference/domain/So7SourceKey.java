package com.tmp.reference.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Normalized SO7 source key used for global color mapping lookup (TMP-SPEC-015 section 6.2).
 *
 * <p>Normalization:
 *
 * <ol>
 *   <li>trim surrounding whitespace ({@link Character#isWhitespace(char)} and U+00A0);
 *   <li>collapse repeated whitespace runs to a single U+0020 space;
 *   <li>uppercase with {@link Locale#ROOT}.
 * </ol>
 *
 * <p>Meaningful spaces, {@code /}, {@code |}, finish suffixes and color codes are preserved.
 * Computed only in Java — PostgreSQL locale-specific case conversion must not be used.
 */
public final class So7SourceKey {

    private static final char NBSP = '\u00A0';

    private final String value;

    private So7SourceKey(String value) {
        this.value = value;
    }

    /**
     * Builds the normalized key from an SO7 source label.
     *
     * @param sourceText original SO7 textual label (not null)
     * @return normalized key
     */
    public static So7SourceKey of(String sourceText) {
        Objects.requireNonNull(sourceText, "sourceText");
        return new So7SourceKey(normalize(sourceText));
    }

    private static String normalize(String text) {
        int length = text.length();
        int start = 0;
        int end = length - 1;
        while (start <= end && isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end >= start && isWhitespace(text.charAt(end))) {
            end--;
        }
        if (start > end) {
            return "";
        }

        StringBuilder collapsed = new StringBuilder(end - start + 1);
        boolean inWhitespace = false;
        for (int i = start; i <= end; i++) {
            char ch = text.charAt(i);
            if (isWhitespace(ch)) {
                if (!inWhitespace) {
                    collapsed.append(' ');
                    inWhitespace = true;
                }
            } else {
                collapsed.append(ch);
                inWhitespace = false;
            }
        }
        return collapsed.toString().toUpperCase(Locale.ROOT);
    }

    private static boolean isWhitespace(char ch) {
        return Character.isWhitespace(ch) || ch == NBSP;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof So7SourceKey that)) {
            return false;
        }
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}