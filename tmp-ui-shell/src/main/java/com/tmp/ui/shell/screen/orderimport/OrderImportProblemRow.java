package com.tmp.ui.shell.screen.orderimport;

import com.tmp.order.api.imports.OrderImportProblem;
import com.tmp.order.api.imports.OrderImportProblemSeverity;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Presentation row for the unified import problems table.
 */
public final class OrderImportProblemRow {

    private static final Map<String, String> KNOWN_FIELD_LABELS =
            Map.ofEntries(
                    Map.entry("orderNumber", "Номер заказа"),
                    Map.entry("orderDate", "Дата заказа"),
                    Map.entry("customerName", "Заказчик"),
                    Map.entry("productCode", "Код позиции"),
                    Map.entry("name", "Наименование"),
                    Map.entry("quantity", "Количество"),
                    Map.entry("externalPositionNumber", "Внешний номер позиции"),
                    Map.entry("materialCode", "Артикул"),
                    Map.entry("materialName", "Наименование материала"),
                    Map.entry("unitOfMeasure", "Единица измерения"),
                    Map.entry("length", "Длина"),
                    Map.entry("color", "Цвет"),
                    Map.entry("positions", "Позиции"),
                    Map.entry("specificationLines", "Строки спецификации"),
                    Map.entry("sourceReference", "Источник"),
                    Map.entry("sourceType", "Тип источника"),
                    Map.entry("contentChecksum", "Контрольная сумма"),
                    Map.entry("batches", "Пакеты"),
                    Map.entry("batch", "Пакет"),
                    Map.entry("file", "Файл"));

    private final OrderImportProblem source;
    private final String typeLabel;
    private final String whereLabel;
    private final String message;

    private OrderImportProblemRow(
            OrderImportProblem source, String typeLabel, String whereLabel, String message) {
        this.source = source;
        this.typeLabel = typeLabel;
        this.whereLabel = whereLabel;
        this.message = message;
    }

    public static OrderImportProblemRow from(OrderImportProblem problem) {
        Objects.requireNonNull(problem, "problem");
        String type =
                problem.severity() == OrderImportProblemSeverity.WARNING
                        ? "● Предупреждение"
                        : "● Ошибка";
        return new OrderImportProblemRow(
                problem, type, buildWhere(problem), problem.message());
    }

    public OrderImportProblem source() {
        return source;
    }

    public String typeLabel() {
        return typeLabel;
    }

    public String whereLabel() {
        return whereLabel;
    }

    public String message() {
        return message;
    }

    public OrderImportProblemSeverity severity() {
        return source.severity();
    }

    static String buildWhere(OrderImportProblem problem) {
        StringBuilder builder = new StringBuilder();
        if (problem.positionIndex() != null) {
            builder.append("Позиция ").append(problem.positionIndex());
        }
        if (problem.specificationLineIndex() != null) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append("строка ").append(problem.specificationLineIndex());
        }
        String field = displayFieldName(problem.fieldName());
        if (field != null && !field.isBlank()) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(field);
        }
        if (builder.length() == 0
                && problem.location() != null
                && !problem.location().isBlank()) {
            return problem.location();
        }
        if (builder.length() == 0) {
            return "";
        }
        return builder.toString();
    }

    static String displayFieldName(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return null;
        }
        String trimmed = fieldName.trim();
        String mapped = KNOWN_FIELD_LABELS.get(trimmed);
        if (mapped != null) {
            return mapped;
        }
        mapped = KNOWN_FIELD_LABELS.get(trimmed.toLowerCase(Locale.ROOT));
        if (mapped != null) {
            return mapped;
        }
        return trimmed;
    }
}
