package com.tmp.order.api.imports;

import java.util.List;
import java.util.Objects;

/**
 * Controlled validation failure for import preview/confirm. Does not expose SQL or stack traces.
 */
public final class OrderImportValidationException extends RuntimeException {

    private final List<OrderImportProblem> problems;

    public OrderImportValidationException(List<OrderImportProblem> problems) {
        super(summarize(problems));
        this.problems = List.copyOf(Objects.requireNonNull(problems, "problems"));
        if (this.problems.isEmpty()) {
            throw new IllegalArgumentException("problems must not be empty");
        }
    }

    private static String summarize(List<OrderImportProblem> problems) {
        Objects.requireNonNull(problems, "problems");
        if (problems.isEmpty()) {
            return "Импорт содержит ошибки проверки.";
        }
        return problems.get(0).message();
    }

    public List<OrderImportProblem> problems() {
        return problems;
    }
}
