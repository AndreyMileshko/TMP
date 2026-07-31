package com.tmp.order.api.imports;

import java.util.Objects;

/**
 * One source-neutral preview problem. User messages are Russian and must not leak SQL, stack
 * traces, Java class names or internal document identifiers.
 */
public final class OrderImportProblem {

    private final String code;
    private final OrderImportProblemSeverity severity;
    private final String location;
    private final Integer positionIndex;
    private final Integer specificationLineIndex;
    private final String fieldName;
    private final String rawValue;
    private final String message;

    private OrderImportProblem(
            String code,
            OrderImportProblemSeverity severity,
            String location,
            Integer positionIndex,
            Integer specificationLineIndex,
            String fieldName,
            String rawValue,
            String message) {
        this.code = code;
        this.severity = severity;
        this.location = location;
        this.positionIndex = positionIndex;
        this.specificationLineIndex = specificationLineIndex;
        this.fieldName = fieldName;
        this.rawValue = rawValue;
        this.message = message;
    }

    public static OrderImportProblem error(
            String code,
            String location,
            Integer positionIndex,
            Integer specificationLineIndex,
            String fieldName,
            String rawValue,
            String message) {
        return of(
                code,
                OrderImportProblemSeverity.ERROR,
                location,
                positionIndex,
                specificationLineIndex,
                fieldName,
                rawValue,
                message);
    }

    public static OrderImportProblem warning(
            String code,
            String location,
            Integer positionIndex,
            Integer specificationLineIndex,
            String fieldName,
            String rawValue,
            String message) {
        return of(
                code,
                OrderImportProblemSeverity.WARNING,
                location,
                positionIndex,
                specificationLineIndex,
                fieldName,
                rawValue,
                message);
    }

    public static OrderImportProblem of(
            String code,
            OrderImportProblemSeverity severity,
            String location,
            Integer positionIndex,
            Integer specificationLineIndex,
            String fieldName,
            String rawValue,
            String message) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        String trimmedCode = code.trim();
        String trimmedMessage = message.trim();
        if (trimmedCode.isEmpty()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (trimmedMessage.isEmpty()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return new OrderImportProblem(
                trimmedCode,
                severity,
                blankToNull(location),
                positionIndex,
                specificationLineIndex,
                blankToNull(fieldName),
                rawValue,
                trimmedMessage);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String code() {
        return code;
    }

    public OrderImportProblemSeverity severity() {
        return severity;
    }

    public String location() {
        return location;
    }

    public Integer positionIndex() {
        return positionIndex;
    }

    public Integer specificationLineIndex() {
        return specificationLineIndex;
    }

    public String fieldName() {
        return fieldName;
    }

    public String rawValue() {
        return rawValue;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrderImportProblem that)) {
            return false;
        }
        return code.equals(that.code)
                && severity == that.severity
                && Objects.equals(location, that.location)
                && Objects.equals(positionIndex, that.positionIndex)
                && Objects.equals(specificationLineIndex, that.specificationLineIndex)
                && Objects.equals(fieldName, that.fieldName)
                && Objects.equals(rawValue, that.rawValue)
                && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                code,
                severity,
                location,
                positionIndex,
                specificationLineIndex,
                fieldName,
                rawValue,
                message);
    }
}
