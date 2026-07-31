package com.tmp.order.application.imports.stxt;

import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportPosition;
import com.tmp.order.api.imports.OrderImportProblem;
import com.tmp.order.api.imports.OrderImportSpecificationLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Parses decoded STXT text into a source-neutral batch and adapter-level problems.
 *
 * <p>Uses structural delimiter {@code " / "} only — never splits on every {@code /}.
 */
final class StxtTableParser {

    static final String SOURCE_TYPE = "STXT";
    static final String DELIMITER = " / ";

    static final String CODE_FILE_EMPTY = "STXT_FILE_EMPTY";
    static final String CODE_ENCODING = "STXT_ENCODING";
    static final String CODE_HEADER_MISSING = "STXT_HEADER_MISSING";
    static final String CODE_HEADER_UNKNOWN = "STXT_HEADER_UNKNOWN";
    static final String CODE_NO_DATA_ROWS = "STXT_NO_DATA_ROWS";
    static final String CODE_COLUMN_COUNT = "STXT_COLUMN_COUNT";
    static final String CODE_MULTIPLE_ORDERS = "STXT_MULTIPLE_ORDERS";
    static final String CODE_ORDER_NUMBER_REQUIRED = "STXT_ORDER_NUMBER_REQUIRED";
    static final String CODE_EXTERNAL_POSITION_REQUIRED = "STXT_EXTERNAL_POSITION_REQUIRED";
    static final String CODE_PRODUCT_QUANTITY_INVALID = "STXT_PRODUCT_QUANTITY_INVALID";
    static final String CODE_PRODUCT_QUANTITY_NOT_POSITIVE = "STXT_PRODUCT_QUANTITY_NOT_POSITIVE";
    static final String CODE_MATERIAL_CODE_REQUIRED = "STXT_MATERIAL_CODE_REQUIRED";
    static final String CODE_MATERIAL_NAME_REQUIRED = "STXT_MATERIAL_NAME_REQUIRED";
    static final String CODE_LENGTH_INVALID = "STXT_LENGTH_INVALID";
    static final String CODE_LENGTH_NOT_POSITIVE = "STXT_LENGTH_NOT_POSITIVE";
    static final String CODE_LINE_QUANTITY_INVALID = "STXT_LINE_QUANTITY_INVALID";
    static final String CODE_LINE_QUANTITY_NOT_POSITIVE = "STXT_LINE_QUANTITY_NOT_POSITIVE";
    static final String CODE_PRODUCT_QUANTITY_MISMATCH = "STXT_PRODUCT_QUANTITY_MISMATCH";

    private static final Map<String, Column> HEADER_ALIASES = buildHeaderAliases();

    private StxtTableParser() {}

    static ParsedTable parse(String text, String sourceReference, String contentChecksum) {
        Objects.requireNonNull(text, "text");
        List<OrderImportProblem> errors = new ArrayList<>();
        List<OrderImportProblem> warnings = new ArrayList<>();

        List<IndexedLine> lines = nonEmptyLines(text);
        if (lines.isEmpty()) {
            errors.add(
                    OrderImportProblem.error(
                            CODE_FILE_EMPTY,
                            "file",
                            null,
                            null,
                            null,
                            null,
                            "Файл выгрузки пуст."));
            return ParsedTable.empty(errors, warnings);
        }

        IndexedLine headerLine = lines.get(0);
        List<String> headerCells = splitFields(headerLine.text());
        Map<Column, Integer> columnIndex = new LinkedHashMap<>();
        for (int i = 0; i < headerCells.size(); i++) {
            String rawHeader = headerCells.get(i);
            String normalized = normalizeHeader(rawHeader);
            Column column = HEADER_ALIASES.get(normalized);
            if (column == null) {
                warnings.add(
                        OrderImportProblem.warning(
                                CODE_HEADER_UNKNOWN,
                                rowLocation(headerLine.number()),
                                null,
                                null,
                                rawHeader.trim(),
                                rawHeader,
                                "Неизвестная колонка «"
                                        + rawHeader.trim()
                                        + "» проигнорирована."));
                continue;
            }
            if (columnIndex.containsKey(column)) {
                warnings.add(
                        OrderImportProblem.warning(
                                CODE_HEADER_UNKNOWN,
                                rowLocation(headerLine.number()),
                                null,
                                null,
                                rawHeader.trim(),
                                rawHeader,
                                "Повтор заголовка «"
                                        + rawHeader.trim()
                                        + "» проигнорирован."));
                continue;
            }
            columnIndex.put(column, i);
        }

        for (Column required : requiredColumns()) {
            if (!columnIndex.containsKey(required)) {
                errors.add(
                        OrderImportProblem.error(
                                CODE_HEADER_MISSING,
                                rowLocation(headerLine.number()),
                                null,
                                null,
                                required.displayName(),
                                null,
                                "Отсутствует обязательная колонка «"
                                        + required.displayName()
                                        + "»."));
            }
        }
        if (!errors.isEmpty()) {
            return ParsedTable.empty(errors, warnings);
        }

        List<RawRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            IndexedLine line = lines.get(i);
            List<String> cells = splitFields(line.text());
            RawRow row = mapRow(line.number(), cells, columnIndex, errors);
            if (row != null) {
                rows.add(row);
            }
        }

        if (rows.isEmpty() && errors.isEmpty()) {
            errors.add(
                    OrderImportProblem.error(
                            CODE_NO_DATA_ROWS,
                            "file",
                            null,
                            null,
                            null,
                            null,
                            "В файле нет строк данных."));
            return ParsedTable.empty(errors, warnings);
        }

        String orderNumber = null;
        for (RawRow row : rows) {
            if (row.orderNumber() == null || row.orderNumber().isBlank()) {
                errors.add(
                        OrderImportProblem.error(
                                CODE_ORDER_NUMBER_REQUIRED,
                                rowLocation(row.lineNumber()),
                                null,
                                null,
                                Column.ORDER_NUMBER.displayName(),
                                row.rawOrderNumber(),
                                "Не задан номер счета."));
                continue;
            }
            String trimmed = row.orderNumber().trim();
            if (orderNumber == null) {
                orderNumber = trimmed;
            } else if (!orderNumber.equals(trimmed)) {
                errors.add(
                        OrderImportProblem.error(
                                CODE_MULTIPLE_ORDERS,
                                rowLocation(row.lineNumber()),
                                null,
                                null,
                                Column.ORDER_NUMBER.displayName(),
                                row.rawOrderNumber(),
                                "В файле несколько номеров заказа («"
                                        + orderNumber
                                        + "» и «"
                                        + trimmed
                                        + "»). Импорт остановлен."));
                return ParsedTable.empty(errors, warnings);
            }
        }

        if (orderNumber == null) {
            return ParsedTable.empty(errors, warnings);
        }

        List<OrderImportPosition> positions = groupPositions(rows, errors);
        if (!errors.isEmpty()) {
            // Still expose partial batch for preview when structure is otherwise usable.
        }
        if (positions.isEmpty()) {
            return ParsedTable.empty(errors, warnings);
        }

        OrderImportBatch batch =
                OrderImportBatch.of(
                        SOURCE_TYPE, sourceReference, contentChecksum, orderNumber, positions);
        return new ParsedTable(batch, errors, warnings);
    }

    private static List<Column> requiredColumns() {
        return List.of(
                Column.ORDER_NUMBER,
                Column.EXTERNAL_POSITION,
                Column.PRODUCT_QUANTITY,
                Column.MATERIAL_CODE,
                Column.MATERIAL_NAME,
                Column.COLOR,
                Column.LENGTH_MM,
                Column.LINE_QUANTITY);
    }

    private static RawRow mapRow(
            int lineNumber,
            List<String> cells,
            Map<Column, Integer> columnIndex,
            List<OrderImportProblem> errors) {
        int maxIndex = columnIndex.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (cells.size() <= maxIndex) {
            errors.add(
                    OrderImportProblem.error(
                            CODE_COLUMN_COUNT,
                            rowLocation(lineNumber),
                            null,
                            null,
                            null,
                            String.join(DELIMITER, cells),
                            "В строке недостаточно колонок."));
            return null;
        }

        String rawOrder = cell(cells, columnIndex, Column.ORDER_NUMBER);
        String rawPosition = cell(cells, columnIndex, Column.EXTERNAL_POSITION);
        String rawProductQty = cell(cells, columnIndex, Column.PRODUCT_QUANTITY);
        String rawMaterialCode = cell(cells, columnIndex, Column.MATERIAL_CODE);
        String rawMaterialName = cell(cells, columnIndex, Column.MATERIAL_NAME);
        String rawColor = cell(cells, columnIndex, Column.COLOR);
        String rawLength = cell(cells, columnIndex, Column.LENGTH_MM);
        String rawLineQty = cell(cells, columnIndex, Column.LINE_QUANTITY);

        boolean rowFailed = false;

        if (isBlank(rawPosition)) {
            errors.add(
                    fieldError(
                            CODE_EXTERNAL_POSITION_REQUIRED,
                            lineNumber,
                            Column.EXTERNAL_POSITION,
                            rawPosition,
                            "Не задан номер изделия."));
            rowFailed = true;
        }

        Integer productQuantity = null;
        if (isBlank(rawProductQty)) {
            errors.add(
                    fieldError(
                            CODE_PRODUCT_QUANTITY_INVALID,
                            lineNumber,
                            Column.PRODUCT_QUANTITY,
                            rawProductQty,
                            "Не задано количество изделий."));
            rowFailed = true;
        } else {
            try {
                productQuantity = parseInteger(rawProductQty);
                if (productQuantity <= 0) {
                    errors.add(
                            fieldError(
                                    CODE_PRODUCT_QUANTITY_NOT_POSITIVE,
                                    lineNumber,
                                    Column.PRODUCT_QUANTITY,
                                    rawProductQty,
                                    "Количество изделий должно быть больше нуля."));
                    rowFailed = true;
                }
            } catch (NumberFormatException ex) {
                errors.add(
                        fieldError(
                                CODE_PRODUCT_QUANTITY_INVALID,
                                lineNumber,
                                Column.PRODUCT_QUANTITY,
                                rawProductQty,
                                "Некорректное количество изделий."));
                rowFailed = true;
            }
        }

        if (isBlank(rawMaterialCode)) {
            errors.add(
                    fieldError(
                            CODE_MATERIAL_CODE_REQUIRED,
                            lineNumber,
                            Column.MATERIAL_CODE,
                            rawMaterialCode,
                            "Не задан артикул."));
            rowFailed = true;
        }

        if (isBlank(rawMaterialName)) {
            errors.add(
                    fieldError(
                            CODE_MATERIAL_NAME_REQUIRED,
                            lineNumber,
                            Column.MATERIAL_NAME,
                            rawMaterialName,
                            "Не задано наименование."));
            rowFailed = true;
        }

        LengthParse lengthParse = parseLengthMm(rawLength);
        if (lengthParse.errorCode() != null) {
            errors.add(
                    fieldError(
                            lengthParse.errorCode(),
                            lineNumber,
                            Column.LENGTH_MM,
                            rawLength,
                            lengthParse.message()));
            rowFailed = true;
        } else if (lengthParse.value() != null
                && lengthParse.value().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(
                    fieldError(
                            CODE_LENGTH_NOT_POSITIVE,
                            lineNumber,
                            Column.LENGTH_MM,
                            rawLength,
                            "Размер должен быть больше нуля либо пустым."));
            rowFailed = true;
        }

        BigDecimal lineQuantity = null;
        if (isBlank(rawLineQty)) {
            errors.add(
                    fieldError(
                            CODE_LINE_QUANTITY_INVALID,
                            lineNumber,
                            Column.LINE_QUANTITY,
                            rawLineQty,
                            "Не задано количество позиции."));
            rowFailed = true;
        } else {
            try {
                lineQuantity = parseDecimal(rawLineQty);
                if (lineQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(
                            fieldError(
                                    CODE_LINE_QUANTITY_NOT_POSITIVE,
                                    lineNumber,
                                    Column.LINE_QUANTITY,
                                    rawLineQty,
                                    "Количество позиции должно быть больше нуля."));
                    rowFailed = true;
                }
            } catch (NumberFormatException ex) {
                errors.add(
                        fieldError(
                                CODE_LINE_QUANTITY_INVALID,
                                lineNumber,
                                Column.LINE_QUANTITY,
                                rawLineQty,
                                "Некорректное количество позиции."));
                rowFailed = true;
            }
        }

        if (rowFailed) {
            return null;
        }

        String color = isBlank(rawColor) ? null : rawColor.trim();
        return new RawRow(
                lineNumber,
                rawOrder,
                isBlank(rawOrder) ? null : rawOrder.trim(),
                rawPosition.trim(),
                productQuantity,
                rawMaterialCode.trim(),
                rawMaterialName.trim(),
                color,
                lengthParse.value(),
                lineQuantity);
    }

    private static List<OrderImportPosition> groupPositions(
            List<RawRow> rows, List<OrderImportProblem> errors) {
        Map<String, PositionBuilder> builders = new LinkedHashMap<>();
        for (RawRow row : rows) {
            PositionBuilder builder =
                    builders.computeIfAbsent(
                            row.externalPositionNumber(),
                            key -> new PositionBuilder(key, row.productQuantity()));
            if (!builder.productQuantity().equals(row.productQuantity())) {
                errors.add(
                        OrderImportProblem.error(
                                CODE_PRODUCT_QUANTITY_MISMATCH,
                                rowLocation(row.lineNumber()),
                                null,
                                null,
                                Column.PRODUCT_QUANTITY.displayName(),
                                String.valueOf(row.productQuantity()),
                                "Для изделия «"
                                        + row.externalPositionNumber()
                                        + "» указаны разные количества изделий."));
                continue;
            }
            builder.lines()
                    .add(
                            OrderImportSpecificationLine.of(
                                    row.materialCode(),
                                    row.materialName(),
                                    row.color(),
                                    row.lengthMm(),
                                    row.lineQuantity()));
        }
        List<OrderImportPosition> positions = new ArrayList<>();
        for (PositionBuilder builder : builders.values()) {
            if (builder.lines().isEmpty()) {
                continue;
            }
            positions.add(
                    OrderImportPosition.of(
                            builder.externalPositionNumber(),
                            builder.productQuantity(),
                            builder.lines()));
        }
        return positions;
    }

    static List<String> splitFields(String line) {
        List<String> fields = new ArrayList<>();
        int start = 0;
        int index;
        while ((index = line.indexOf(DELIMITER, start)) >= 0) {
            fields.add(line.substring(start, index));
            start = index + DELIMITER.length();
        }
        fields.add(line.substring(start));
        return fields;
    }

    static LengthParse parseLengthMm(String raw) {
        if (isBlank(raw)) {
            return LengthParse.ok(null);
        }
        String normalized = raw.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.endsWith("мм.")) {
            normalized = normalized.substring(0, normalized.length() - 3).trim();
        } else if (lower.endsWith("мм")) {
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        }
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        normalized = normalized.replace(',', '.');
        if (normalized.isEmpty()) {
            return LengthParse.ok(null);
        }
        try {
            return LengthParse.ok(new BigDecimal(normalized));
        } catch (NumberFormatException ex) {
            return LengthParse.error(
                    CODE_LENGTH_INVALID, "Некорректный размер. Ожидается число в миллиметрах.");
        }
    }

    private static Integer parseInteger(String raw) {
        String normalized = raw.trim().replace(',', '.');
        BigDecimal decimal = new BigDecimal(normalized);
        try {
            return decimal.intValueExact();
        } catch (ArithmeticException ex) {
            throw new NumberFormatException("not an integer");
        }
    }

    private static BigDecimal parseDecimal(String raw) {
        return new BigDecimal(raw.trim().replace(',', '.'));
    }

    private static String cell(List<String> cells, Map<Column, Integer> columnIndex, Column column) {
        Integer index = columnIndex.get(column);
        if (index == null || index < 0 || index >= cells.size()) {
            return null;
        }
        return cells.get(index);
    }

    private static OrderImportProblem fieldError(
            String code, int lineNumber, Column column, String rawValue, String message) {
        return OrderImportProblem.error(
                code,
                rowLocation(lineNumber),
                null,
                null,
                column.displayName(),
                rawValue,
                message);
    }

    private static String rowLocation(int lineNumber) {
        return "строка " + lineNumber;
    }

    private static List<IndexedLine> nonEmptyLines(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] parts = normalized.split("\n", -1);
        List<IndexedLine> lines = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            String line = parts[i];
            if (line == null || line.isBlank()) {
                continue;
            }
            lines.add(new IndexedLine(i + 1, line));
        }
        return lines;
    }

    static String normalizeHeader(String header) {
        String value = header == null ? "" : header.trim().toLowerCase(Locale.ROOT);
        value = value.replace('ё', 'е');
        value = value.replaceAll("\\s+", " ");
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Map<String, Column> buildHeaderAliases() {
        Map<String, Column> map = new LinkedHashMap<>();
        putAliases(map, Column.ORDER_NUMBER, "Счет", "Счёт", "Номер счета", "Номер счёта", "Заказ");
        putAliases(map, Column.EXTERNAL_POSITION, "Изделие", "Позиция", "Номер изделия");
        putAliases(map, Column.PRODUCT_QUANTITY, "Кол-во изд.", "Количество изделий");
        putAliases(map, Column.MATERIAL_CODE, "Артикул", "Код материала");
        putAliases(map, Column.MATERIAL_NAME, "Наименование", "Название");
        putAliases(map, Column.COLOR, "Цвет");
        putAliases(map, Column.LENGTH_MM, "Размер", "Размер, мм", "Длина", "Длина, мм");
        putAliases(
                map,
                Column.LINE_QUANTITY,
                "Кол-во позиции",
                "Количество позиции",
                "Количество");
        return Map.copyOf(map);
    }

    private static void putAliases(Map<String, Column> map, Column column, String... aliases) {
        for (String alias : aliases) {
            map.put(normalizeHeader(alias), column);
        }
    }

    private enum Column {
        ORDER_NUMBER("Счет"),
        EXTERNAL_POSITION("Изделие"),
        PRODUCT_QUANTITY("Кол-во изд."),
        MATERIAL_CODE("Артикул"),
        MATERIAL_NAME("Наименование"),
        COLOR("Цвет"),
        LENGTH_MM("Размер"),
        LINE_QUANTITY("Кол-во позиции");

        private final String displayName;

        Column(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }
    }

    record ParsedTable(
            OrderImportBatch batch,
            List<OrderImportProblem> errors,
            List<OrderImportProblem> warnings) {

        static ParsedTable empty(
                List<OrderImportProblem> errors, List<OrderImportProblem> warnings) {
            return new ParsedTable(null, errors, warnings);
        }
    }

    private record IndexedLine(int number, String text) {}

    private record RawRow(
            int lineNumber,
            String rawOrderNumber,
            String orderNumber,
            String externalPositionNumber,
            Integer productQuantity,
            String materialCode,
            String materialName,
            String color,
            BigDecimal lengthMm,
            BigDecimal lineQuantity) {}

    private static final class PositionBuilder {
        private final String externalPositionNumber;
        private final Integer productQuantity;
        private final List<OrderImportSpecificationLine> lines = new ArrayList<>();

        private PositionBuilder(String externalPositionNumber, Integer productQuantity) {
            this.externalPositionNumber = externalPositionNumber;
            this.productQuantity = productQuantity;
        }

        String externalPositionNumber() {
            return externalPositionNumber;
        }

        Integer productQuantity() {
            return productQuantity;
        }

        List<OrderImportSpecificationLine> lines() {
            return lines;
        }
    }

    record LengthParse(BigDecimal value, String errorCode, String message) {
        static LengthParse ok(BigDecimal value) {
            return new LengthParse(value, null, null);
        }

        static LengthParse error(String code, String message) {
            return new LengthParse(null, code, message);
        }
    }
}
