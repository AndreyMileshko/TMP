package com.tmp.order.application.imports.stxt;

import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportProblem;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of STXT file parsing. Technical exceptions are not exposed; problems carry row/column
 * context and Russian user messages.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "errors and warnings are unmodifiable defensive copies.")
public final class StxtParseResult {

    private final OrderImportBatch batch;
    private final List<OrderImportProblem> errors;
    private final List<OrderImportProblem> warnings;
    private final String detectedEncoding;

    private StxtParseResult(
            OrderImportBatch batch,
            List<OrderImportProblem> errors,
            List<OrderImportProblem> warnings,
            String detectedEncoding) {
        this.batch = batch;
        this.errors = errors;
        this.warnings = warnings;
        this.detectedEncoding = detectedEncoding;
    }

    public static StxtParseResult of(
            OrderImportBatch batch,
            List<OrderImportProblem> errors,
            List<OrderImportProblem> warnings,
            String detectedEncoding) {
        Objects.requireNonNull(errors, "errors");
        Objects.requireNonNull(warnings, "warnings");
        return new StxtParseResult(
                batch, List.copyOf(errors), List.copyOf(warnings), detectedEncoding);
    }

    public Optional<OrderImportBatch> batch() {
        return Optional.ofNullable(batch);
    }

    public List<OrderImportProblem> errors() {
        return errors;
    }

    public List<OrderImportProblem> warnings() {
        return warnings;
    }

    public Optional<String> detectedEncoding() {
        return Optional.ofNullable(detectedEncoding);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /** True when a batch was built and there are no adapter errors. */
    public boolean isSuccessful() {
        return batch != null && errors.isEmpty();
    }
}
