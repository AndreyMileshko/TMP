package com.tmp.order.application.imports;

import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportPosition;
import com.tmp.order.api.imports.OrderImportProblem;
import com.tmp.order.api.imports.OrderImportSpecificationLine;
import com.tmp.order.domain.CommercialPlaceholderValidator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Structural validation of a source-neutral {@link OrderImportBatch} for ACTIVE import (Final STXT
 * Contract). Does not touch persistence. Placeholders ({@code UNKNOWN}, {@code N/A}, …) reject the
 * import.
 */
public class OrderImportValidator {

    public static final String CODE_SOURCE_TYPE_REQUIRED = "IMPORT_SOURCE_TYPE_REQUIRED";
    public static final String CODE_SOURCE_REFERENCE_REQUIRED = "IMPORT_SOURCE_REFERENCE_REQUIRED";
    public static final String CODE_SOURCE_TYPE_TOO_LONG = "IMPORT_SOURCE_TYPE_TOO_LONG";
    public static final String CODE_SOURCE_REFERENCE_TOO_LONG = "IMPORT_SOURCE_REFERENCE_TOO_LONG";
    public static final String CODE_SOURCE_REFERENCE_ABSOLUTE_PATH =
            "IMPORT_SOURCE_REFERENCE_ABSOLUTE_PATH";
    public static final String CODE_CHECKSUM_REQUIRED = "IMPORT_CHECKSUM_REQUIRED";
    public static final String CODE_CHECKSUM_TOO_LONG = "IMPORT_CHECKSUM_TOO_LONG";
    public static final String CODE_ORDER_NUMBER_REQUIRED = "IMPORT_ORDER_NUMBER_REQUIRED";
    public static final String CODE_ORDER_DATE_REQUIRED = "IMPORT_ORDER_DATE_REQUIRED";
    public static final String CODE_CUSTOMER_REQUIRED = "IMPORT_CUSTOMER_REQUIRED";
    public static final String CODE_POSITIONS_EMPTY = "IMPORT_POSITIONS_EMPTY";
    public static final String CODE_EXTERNAL_POSITION_REQUIRED = "IMPORT_EXTERNAL_POSITION_REQUIRED";
    public static final String CODE_PRODUCT_CODE_REQUIRED = "IMPORT_PRODUCT_CODE_REQUIRED";
    public static final String CODE_PRODUCT_NAME_REQUIRED = "IMPORT_PRODUCT_NAME_REQUIRED";
    public static final String CODE_PRODUCT_QUANTITY_REQUIRED = "IMPORT_PRODUCT_QUANTITY_REQUIRED";
    public static final String CODE_PRODUCT_QUANTITY_NOT_POSITIVE =
            "IMPORT_PRODUCT_QUANTITY_NOT_POSITIVE";
    public static final String CODE_SPECIFICATION_EMPTY = "IMPORT_SPECIFICATION_EMPTY";
    public static final String CODE_MATERIAL_CODE_REQUIRED = "IMPORT_MATERIAL_CODE_REQUIRED";
    public static final String CODE_MATERIAL_NAME_REQUIRED = "IMPORT_MATERIAL_NAME_REQUIRED";
    public static final String CODE_UNIT_REQUIRED = "IMPORT_UNIT_REQUIRED";
    public static final String CODE_LENGTH_MM_NOT_POSITIVE = "IMPORT_LENGTH_MM_NOT_POSITIVE";
    public static final String CODE_LINE_QUANTITY_REQUIRED = "IMPORT_LINE_QUANTITY_REQUIRED";
    public static final String CODE_LINE_QUANTITY_NOT_POSITIVE =
            "IMPORT_LINE_QUANTITY_NOT_POSITIVE";
    public static final String CODE_BATCHES_EMPTY = "IMPORT_BATCHES_EMPTY";
    public static final String CODE_PLACEHOLDER_FORBIDDEN = "IMPORT_PLACEHOLDER_FORBIDDEN";

    public static final int MAX_SOURCE_TYPE_LENGTH = 64;
    public static final int MAX_CONTENT_CHECKSUM_LENGTH = 128;
    public static final int MAX_SOURCE_REFERENCE_LENGTH = 512;

    public List<OrderImportProblem> validate(OrderImportBatch batch) {
        List<OrderImportProblem> problems = new ArrayList<>();
        if (batch == null) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_ORDER_NUMBER_REQUIRED,
                            "batch",
                            null,
                            null,
                            "batch",
                            null,
                            "Пакет импорта не задан."));
            return problems;
        }
        validateBatch(batch, "batch", problems);
        return problems;
    }

    public List<OrderImportProblem> validateAll(List<OrderImportBatch> batches) {
        List<OrderImportProblem> problems = new ArrayList<>();
        if (batches == null || batches.isEmpty()) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_BATCHES_EMPTY,
                            "file",
                            null,
                            null,
                            "batches",
                            null,
                            "Список заказов для импорта пуст."));
            return problems;
        }
        for (int i = 0; i < batches.size(); i++) {
            validateBatch(batches.get(i), "order[" + i + "]", problems);
        }
        return problems;
    }

    private void validateBatch(
            OrderImportBatch batch, String location, List<OrderImportProblem> problems) {
        if (isBlank(batch.sourceType())) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_SOURCE_TYPE_REQUIRED,
                            location,
                            null,
                            null,
                            "sourceType",
                            batch.sourceType(),
                            "Тип источника импорта не задан."));
        } else if (batch.sourceType().trim().length() > MAX_SOURCE_TYPE_LENGTH) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_SOURCE_TYPE_TOO_LONG,
                            location,
                            null,
                            null,
                            "sourceType",
                            batch.sourceType(),
                            "Тип источника импорта превышает допустимую длину."));
        }
        if (isBlank(batch.sourceReference())) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_SOURCE_REFERENCE_REQUIRED,
                            location,
                            null,
                            null,
                            "sourceReference",
                            batch.sourceReference(),
                            "Ссылка на источник импорта не задана."));
        } else {
            String reference = batch.sourceReference().trim();
            if (reference.length() > MAX_SOURCE_REFERENCE_LENGTH) {
                problems.add(
                        OrderImportProblem.error(
                                CODE_SOURCE_REFERENCE_TOO_LONG,
                                location,
                                null,
                                null,
                                "sourceReference",
                                batch.sourceReference(),
                                "Ссылка на источник импорта превышает допустимую длину."));
            }
            if (looksLikeAbsolutePath(reference)) {
                problems.add(
                        OrderImportProblem.error(
                                CODE_SOURCE_REFERENCE_ABSOLUTE_PATH,
                                location,
                                null,
                                null,
                                "sourceReference",
                                batch.sourceReference(),
                                "Ссылка на источник не должна быть абсолютным путём."));
            }
        }
        if (isBlank(batch.contentChecksum())) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_CHECKSUM_REQUIRED,
                            location,
                            null,
                            null,
                            "contentChecksum",
                            batch.contentChecksum(),
                            "Контрольная сумма содержимого не задана."));
        } else if (batch.contentChecksum().trim().length() > MAX_CONTENT_CHECKSUM_LENGTH) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_CHECKSUM_TOO_LONG,
                            location,
                            null,
                            null,
                            "contentChecksum",
                            batch.contentChecksum(),
                            "Контрольная сумма содержимого превышает допустимую длину."));
        }
        if (isBlank(batch.orderNumber())) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_ORDER_NUMBER_REQUIRED,
                            location,
                            null,
                            null,
                            "orderNumber",
                            batch.orderNumber(),
                            "Номер заказа не задан."));
        } else {
            rejectPlaceholder(
                    batch.orderNumber(),
                    location,
                    null,
                    null,
                    "orderNumber",
                    "Номер заказа не должен быть заглушкой.",
                    problems);
        }
        if (batch.orderDate() == null) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_ORDER_DATE_REQUIRED,
                            location,
                            null,
                            null,
                            "orderDate",
                            null,
                            "Дата заказа не задана."));
        }
        if (isBlank(batch.customerName())) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_CUSTOMER_REQUIRED,
                            location,
                            null,
                            null,
                            "customerName",
                            batch.customerName(),
                            "Клиент не задан."));
        } else {
            rejectPlaceholder(
                    batch.customerName(),
                    location,
                    null,
                    null,
                    "customerName",
                    "Клиент не должен быть заглушкой.",
                    problems);
        }
        if (batch.positions().isEmpty()) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_POSITIONS_EMPTY,
                            location,
                            null,
                            null,
                            "positions",
                            null,
                            "Список позиций импорта пуст."));
            return;
        }
        for (int positionIndex = 0; positionIndex < batch.positions().size(); positionIndex++) {
            validatePosition(
                    batch.positions().get(positionIndex), location, positionIndex, problems);
        }
    }

    private void validatePosition(
            OrderImportPosition position,
            String batchLocation,
            int positionIndex,
            List<OrderImportProblem> problems) {
        String location = batchLocation + ".position[" + positionIndex + "]";
        if (isBlank(position.externalPositionNumber())) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_EXTERNAL_POSITION_REQUIRED,
                            location,
                            positionIndex,
                            null,
                            "externalPositionNumber",
                            position.externalPositionNumber(),
                            "Внешний номер позиции не задан."));
        } else {
            rejectPlaceholder(
                    position.externalPositionNumber(),
                    location,
                    positionIndex,
                    null,
                    "externalPositionNumber",
                    "Внешний номер позиции не должен быть заглушкой.",
                    problems);
        }
        if (isBlank(position.productCode())) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_PRODUCT_CODE_REQUIRED,
                            location,
                            positionIndex,
                            null,
                            "productCode",
                            position.productCode(),
                            "Код изделия не задан."));
        } else {
            rejectPlaceholder(
                    position.productCode(),
                    location,
                    positionIndex,
                    null,
                    "productCode",
                    "Код изделия не должен быть заглушкой.",
                    problems);
        }
        if (isBlank(position.name())) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_PRODUCT_NAME_REQUIRED,
                            location,
                            positionIndex,
                            null,
                            "name",
                            position.name(),
                            "Наименование изделия не задано."));
        } else {
            rejectPlaceholder(
                    position.name(),
                    location,
                    positionIndex,
                    null,
                    "name",
                    "Наименование изделия не должно быть заглушкой.",
                    problems);
        }
        Integer productQuantity = position.quantity();
        if (productQuantity == null) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_PRODUCT_QUANTITY_REQUIRED,
                            location,
                            positionIndex,
                            null,
                            "quantity",
                            null,
                            "Количество изделий не задано."));
        } else if (productQuantity <= 0) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_PRODUCT_QUANTITY_NOT_POSITIVE,
                            location,
                            positionIndex,
                            null,
                            "quantity",
                            String.valueOf(productQuantity),
                            "Количество изделий должно быть целым числом больше нуля."));
        }
        if (position.specificationLines().isEmpty()) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_SPECIFICATION_EMPTY,
                            location,
                            positionIndex,
                            null,
                            "specificationLines",
                            null,
                            "Спецификация позиции пуста."));
            return;
        }
        for (int lineIndex = 0; lineIndex < position.specificationLines().size(); lineIndex++) {
            validateLine(
                    position.specificationLines().get(lineIndex),
                    positionIndex,
                    lineIndex,
                    location,
                    problems);
        }
    }

    private void validateLine(
            OrderImportSpecificationLine line,
            int positionIndex,
            int lineIndex,
            String positionLocation,
            List<OrderImportProblem> problems) {
        String location = positionLocation + ".line[" + lineIndex + "]";
        if (isBlank(line.materialCode())) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_MATERIAL_CODE_REQUIRED,
                            location,
                            positionIndex,
                            lineIndex,
                            "materialCode",
                            line.materialCode(),
                            "Артикул материала не задан."));
        } else {
            rejectPlaceholder(
                    line.materialCode(),
                    location,
                    positionIndex,
                    lineIndex,
                    "materialCode",
                    "Артикул материала не должен быть заглушкой.",
                    problems);
        }
        if (isBlank(line.materialName())) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_MATERIAL_NAME_REQUIRED,
                            location,
                            positionIndex,
                            lineIndex,
                            "materialName",
                            line.materialName(),
                            "Наименование материала не задано."));
        } else {
            rejectPlaceholder(
                    line.materialName(),
                    location,
                    positionIndex,
                    lineIndex,
                    "materialName",
                    "Наименование материала не должно быть заглушкой.",
                    problems);
        }
        if (isBlank(line.unitOfMeasure())) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_UNIT_REQUIRED,
                            location,
                            positionIndex,
                            lineIndex,
                            "unitOfMeasure",
                            line.unitOfMeasure(),
                            "Единица измерения не задана."));
        } else {
            rejectPlaceholder(
                    line.unitOfMeasure(),
                    location,
                    positionIndex,
                    lineIndex,
                    "unitOfMeasure",
                    "Единица измерения не должна быть заглушкой.",
                    problems);
        }
        BigDecimal lengthMm = line.length();
        if (lengthMm != null && lengthMm.signum() <= 0) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_LENGTH_MM_NOT_POSITIVE,
                            location,
                            positionIndex,
                            lineIndex,
                            "length",
                            lengthMm.toPlainString(),
                            "Длина в миллиметрах должна быть больше нуля."));
        }
        BigDecimal lineQuantity = line.quantity();
        if (lineQuantity == null) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_LINE_QUANTITY_REQUIRED,
                            location,
                            positionIndex,
                            lineIndex,
                            "quantity",
                            null,
                            "Количество строки спецификации не задано."));
        } else if (lineQuantity.signum() <= 0) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_LINE_QUANTITY_NOT_POSITIVE,
                            location,
                            positionIndex,
                            lineIndex,
                            "quantity",
                            lineQuantity.toPlainString(),
                            "Количество строки спецификации должно быть больше нуля."));
        }
    }

    private static void rejectPlaceholder(
            String value,
            String location,
            Integer positionIndex,
            Integer lineIndex,
            String field,
            String message,
            List<OrderImportProblem> problems) {
        if (CommercialPlaceholderValidator.isForbiddenPlaceholder(value)) {
            problems.add(
                    OrderImportProblem.error(
                            CODE_PLACEHOLDER_FORBIDDEN,
                            location,
                            positionIndex,
                            lineIndex,
                            field,
                            value,
                            message));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Rejects absolute filesystem paths. Import Core accepts logical source references (e.g. file
     * names) only.
     */
    static boolean looksLikeAbsolutePath(String reference) {
        String value = reference.trim();
        if (value.startsWith("/") || value.startsWith("\\")) {
            return true;
        }
        if (value.length() >= 3
                && Character.isLetter(value.charAt(0))
                && value.charAt(1) == ':'
                && (value.charAt(2) == '\\' || value.charAt(2) == '/')) {
            return true;
        }
        String lower = value.toLowerCase();
        return lower.startsWith("file:")
                || lower.startsWith("/home/")
                || lower.startsWith("/tmp/")
                || lower.startsWith("/var/")
                || lower.startsWith("/usr/");
    }
}
