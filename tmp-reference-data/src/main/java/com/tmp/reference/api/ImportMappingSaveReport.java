package com.tmp.reference.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-key results of {@link So7ColorMappingCommandApi#saveMappingsFromImport} (TMP-SPEC-015 section
 * 11).
 */
public final class ImportMappingSaveReport {

    /** Outcome for one normalized SO7 source key. */
    public enum Result {
        SAVED,
        NOT_SAVED_MAPPING_EXISTS,
        NOT_SAVED_CONFLICT_IN_IMPORT,
        NOT_SAVED_COLOR_INACTIVE,
        NOT_SAVED_INVALID_SOURCE
    }

    /** One import candidate: original source label plus inside/outside catalog color ids. */
    public static final class Candidate {

        private final String sourceText;
        private final UUID insideColorId;
        private final UUID outsideColorId;

        public Candidate(String sourceText, UUID insideColorId, UUID outsideColorId) {
            this.sourceText = sourceText;
            this.insideColorId = insideColorId;
            this.outsideColorId = outsideColorId;
        }

        public String sourceText() {
            return sourceText;
        }

        public UUID insideColorId() {
            return insideColorId;
        }

        public UUID outsideColorId() {
            return outsideColorId;
        }
    }

    /** Result entry for one logical source key (or invalid candidate). */
    public static final class Entry {

        private final String sourceText;
        private final String sourceKey;
        private final Result result;

        public Entry(String sourceText, String sourceKey, Result result) {
            this.sourceText = sourceText;
            this.sourceKey = sourceKey;
            this.result = Objects.requireNonNull(result, "result");
        }

        public String sourceText() {
            return sourceText;
        }

        public String sourceKey() {
            return sourceKey;
        }

        public Result result() {
            return result;
        }
    }

    private final List<Entry> entries;
    private final int savedCount;

    public ImportMappingSaveReport(List<Entry> entries) {
        Objects.requireNonNull(entries, "entries");
        this.entries = List.copyOf(entries);
        int saved = 0;
        for (Entry entry : this.entries) {
            if (entry.result() == Result.SAVED) {
                saved++;
            }
        }
        this.savedCount = saved;
    }

    public List<Entry> entries() {
        return entries;
    }

    public int savedCount() {
        return savedCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<Entry> entries = new ArrayList<>();

        public Builder add(String sourceText, String sourceKey, Result result) {
            entries.add(new Entry(sourceText, sourceKey, result));
            return this;
        }

        public ImportMappingSaveReport build() {
            return new ImportMappingSaveReport(Collections.unmodifiableList(new ArrayList<>(entries)));
        }
    }
}