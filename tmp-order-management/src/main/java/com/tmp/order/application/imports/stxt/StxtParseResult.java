package com.tmp.order.application.imports.stxt;

import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportProblem;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of STXT file parsing (Final STXT Contract). One file may contain several orders.
 * Technical exceptions are not exposed; problems carry row/column context and Russian user
 * messages.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "errors/warnings/batches are unmodifiable defensive copies.")
public final class StxtParseResult {

    private final List<OrderImportBatch> batches;
    private final List<OrderImportProblem> errors;
    private final List<OrderImportProblem> warnings;
    private final String detectedEncoding;

    private StxtParseResult(
            List<OrderImportBatch> batches,
            List<OrderImportProblem> errors,
            List<OrderImportProblem> warnings,
            String detectedEncoding) {
        this.batches = batches;
        this.errors = errors;
        this.warnings = warnings;
        this.detectedEncoding = detectedEncoding;
    }

    public static StxtParseResult of(
            List<OrderImportBatch> batches,
            List<OrderImportProblem> errors,
            List<OrderImportProblem> warnings,
            String detectedEncoding) {
        Objects.requireNonNull(batches, "batches");
        Objects.requireNonNull(errors, "errors");
        Objects.requireNonNull(warnings, "warnings");
        return new StxtParseResult(
                List.copyOf(batches), List.copyOf(errors), List.copyOf(warnings), detectedEncoding);
    }

    /** @deprecated use {@link #of(List, List, List, String)} */
    @Deprecated
    public static StxtParseResult of(
            OrderImportBatch batch,
            List<OrderImportProblem> errors,
            List<OrderImportProblem> warnings,
            String detectedEncoding) {
        List<OrderImportBatch> batches = batch == null ? List.of() : List.of(batch);
        return of(batches, errors, warnings, detectedEncoding);
    }

    public List<OrderImportBatch> batches() {
        return batches;
    }

    /** First batch when present (single-order convenience). */
    public Optional<OrderImportBatch> batch() {
        return batches.isEmpty() ? Optional.empty() : Optional.of(batches.get(0));
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

    /** True when at least one batch was built and there are no adapter errors. */
    public boolean isSuccessful() {
        return !batches.isEmpty() && errors.isEmpty();
    }
}
