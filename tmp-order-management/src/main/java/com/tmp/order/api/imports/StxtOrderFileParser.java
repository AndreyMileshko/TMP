package com.tmp.order.api.imports;

import java.nio.file.Path;

/**
 * Public STXT file parsing port for Import UI and other callers outside Order Management
 * application packages (ADR-029). Implementations live in the STXT adapter module; this type
 * exposes only source-neutral {@link OrderImportBatch} / {@link OrderImportProblem} results.
 */
public interface StxtOrderFileParser {

    /**
     * Reads and parses a STXT export file. {@code sourceReference} for Import Core is the file
     * name only. Does not persist and does not call {@link OrderImportService#confirm}.
     */
    OrderImportFileParseResult parseFile(Path file);

    /**
     * Parses raw STXT bytes with an explicit source reference (typically file name only).
     */
    OrderImportFileParseResult parse(byte[] content, String sourceReference);
}
