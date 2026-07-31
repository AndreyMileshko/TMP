package com.tmp.order.application.imports.stxt;

import com.tmp.order.api.imports.OrderImportFileParseResult;
import com.tmp.order.api.imports.StxtOrderFileParser;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Public-API facade over {@link StxtFileAdapter}. Does not alter adapter behaviour; exists so
 * bootstrap / UI depend only on {@code com.tmp.order.api.imports}.
 */
public final class DefaultStxtOrderFileParser implements StxtOrderFileParser {

    private final StxtFileAdapter adapter;

    public DefaultStxtOrderFileParser(StxtFileAdapter adapter) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
    }

    @Override
    public OrderImportFileParseResult parseFile(Path file) {
        return toPublic(adapter.parseFile(file));
    }

    @Override
    public OrderImportFileParseResult parse(byte[] content, String sourceReference) {
        return toPublic(adapter.parse(content, sourceReference));
    }

    private static OrderImportFileParseResult toPublic(StxtParseResult result) {
        return OrderImportFileParseResult.of(
                result.batch().orElse(null),
                result.errors(),
                result.warnings(),
                result.detectedEncoding().orElse(null));
    }
}
