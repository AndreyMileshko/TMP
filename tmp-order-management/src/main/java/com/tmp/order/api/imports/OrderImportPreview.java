package com.tmp.order.api.imports;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Preview of a source-neutral import batch. Produced without persistence. A prepared plan is
 * present only when there are no ERROR-level problems ({@link #canConfirm()}).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "errors/warnings are unmodifiable defensive copies.")
public final class OrderImportPreview {

    private final String sourceReference;
    private final String orderNumber;
    private final int positionCount;
    private final BigDecimal totalProductQuantity;
    private final int specificationLineCount;
    private final List<OrderImportProblem> errors;
    private final List<OrderImportProblem> warnings;
    private final PreparedOrderImportPlan preparedPlan;

    private OrderImportPreview(
            String sourceReference,
            String orderNumber,
            int positionCount,
            BigDecimal totalProductQuantity,
            int specificationLineCount,
            List<OrderImportProblem> errors,
            List<OrderImportProblem> warnings,
            PreparedOrderImportPlan preparedPlan) {
        this.sourceReference = sourceReference;
        this.orderNumber = orderNumber;
        this.positionCount = positionCount;
        this.totalProductQuantity = totalProductQuantity;
        this.specificationLineCount = specificationLineCount;
        this.errors = errors;
        this.warnings = warnings;
        this.preparedPlan = preparedPlan;
    }

    public static OrderImportPreview of(
            String sourceReference,
            String orderNumber,
            int positionCount,
            BigDecimal totalProductQuantity,
            int specificationLineCount,
            List<OrderImportProblem> errors,
            List<OrderImportProblem> warnings,
            PreparedOrderImportPlan preparedPlan) {
        Objects.requireNonNull(errors, "errors");
        Objects.requireNonNull(warnings, "warnings");
        Objects.requireNonNull(totalProductQuantity, "totalProductQuantity");
        List<OrderImportProblem> errorCopy = List.copyOf(errors);
        List<OrderImportProblem> warningCopy = List.copyOf(warnings);
        if (!errorCopy.isEmpty() && preparedPlan != null) {
            throw new IllegalArgumentException(
                    "Prepared import plan must be absent when preview has errors");
        }
        if (errorCopy.isEmpty() && preparedPlan == null) {
            throw new IllegalArgumentException(
                    "Prepared import plan is required when preview has no errors");
        }
        return new OrderImportPreview(
                sourceReference,
                orderNumber,
                positionCount,
                totalProductQuantity,
                specificationLineCount,
                errorCopy,
                warningCopy,
                preparedPlan);
    }

    public String sourceReference() {
        return sourceReference;
    }

    public String orderNumber() {
        return orderNumber;
    }

    public int positionCount() {
        return positionCount;
    }

    public BigDecimal totalProductQuantity() {
        return totalProductQuantity;
    }

    public int specificationLineCount() {
        return specificationLineCount;
    }

    public List<OrderImportProblem> errors() {
        return errors;
    }

    public List<OrderImportProblem> warnings() {
        return warnings;
    }

    public int errorCount() {
        return errors.size();
    }

    public int warningCount() {
        return warnings.size();
    }

    public boolean canConfirm() {
        return errors.isEmpty() && preparedPlan != null;
    }

    public Optional<PreparedOrderImportPlan> preparedPlan() {
        return Optional.ofNullable(preparedPlan);
    }
}
