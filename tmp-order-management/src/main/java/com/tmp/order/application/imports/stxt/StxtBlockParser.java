package com.tmp.order.application.imports.stxt;

import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportPosition;
import com.tmp.order.api.imports.OrderImportProblem;
import com.tmp.order.api.imports.OrderImportSpecificationLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Final STXT Contract parser (STAGE5-058): block structure {@code ORDER → ITEM → SPECIFICATION}.
 *
 * <p>Supports multiple orders per file. Skips SuperOkna command/comment lines starting with {@code
 * @} or {@code #}. Does not multiply specification quantity by item quantity.
 */
final class StxtBlockParser {

    static final String SOURCE_TYPE = "STXT";
    static final String DELIMITER = " / ";

    static final String CODE_FILE_EMPTY = "STXT_FILE_EMPTY";
    static final String CODE_ENCODING = "STXT_ENCODING";
    static final String CODE_NO_ORDERS = "STXT_NO_ORDERS";
    static final String CODE_ORDER_NUMBER_REQUIRED = "STXT_ORDER_NUMBER_REQUIRED";
    static final String CODE_ORDER_DATE_REQUIRED = "STXT_ORDER_DATE_REQUIRED";
    static final String CODE_ORDER_DATE_INVALID = "STXT_ORDER_DATE_INVALID";
    static final String CODE_READY_DATE_INVALID = "STXT_READY_DATE_INVALID";
    static final String CODE_CUSTOMER_REQUIRED = "STXT_CUSTOMER_REQUIRED";
    static final String CODE_DUPLICATE_ORDER_IN_FILE = "STXT_DUPLICATE_ORDER_IN_FILE";
    static final String CODE_NO_ITEMS = "STXT_NO_ITEMS";
    static final String CODE_EXTERNAL_POSITION_REQUIRED = "STXT_EXTERNAL_POSITION_REQUIRED";
    static final String CODE_PRODUCT_CODE_REQUIRED = "STXT_PRODUCT_CODE_REQUIRED";
    static final String CODE_PRODUCT_NAME_REQUIRED = "STXT_PRODUCT_NAME_REQUIRED";
    static final String CODE_PRODUCT_QUANTITY_INVALID = "STXT_PRODUCT_QUANTITY_INVALID";
    static final String CODE_PRODUCT_QUANTITY_NOT_POSITIVE = "STXT_PRODUCT_QUANTITY_NOT_POSITIVE";
    static final String CODE_SPEC_HEADER_MISSING = "STXT_SPEC_HEADER_MISSING";
    static final String CODE_SPEC_HEADER_UNKNOWN = "STXT_SPEC_HEADER_UNKNOWN";
    static final String CODE_COLUMN_COUNT = "STXT_COLUMN_COUNT";
    static final String CODE_MATERIAL_CODE_REQUIRED = "STXT_MATERIAL_CODE_REQUIRED";
    static final String CODE_MATERIAL_NAME_REQUIRED = "STXT_MATERIAL_NAME_REQUIRED";
    static final String CODE_UNIT_REQUIRED = "STXT_UNIT_REQUIRED";
    static final String CODE_LINE_QUANTITY_INVALID = "STXT_LINE_QUANTITY_INVALID";
    static final String CODE_LINE_QUANTITY_NOT_POSITIVE = "STXT_LINE_QUANTITY_NOT_POSITIVE";
    static final String CODE_LENGTH_INVALID = "STXT_LENGTH_INVALID";
    static final String CODE_NO_SPEC_LINES = "STXT_NO_SPEC_LINES";

    static final String UNIT_SQ_M = "кв.м.";
    static final String UNIT_PIECE_AFTER_SQ_M = "шт.";

    private static final Pattern LABEL_PATTERN =
            Pattern.compile("^\\s*([^:：]+)[:：]\\s*(.*)$");

    private static final DateTimeFormatter[] DATE_FORMATS =
            new DateTimeFormatter[] {
                DateTimeFormatter.ofPattern("d.M.uuuu"),
                DateTimeFormatter.ofPattern("dd.MM.uuuu"),
                DateTimeFormatter.ofPattern("d.M.uu"),
                DateTimeFormatter.ofPattern("dd.MM.uu")
            };

    private static final Map<String, SpecColumn> HEADER_ALIASES = buildHeaderAliases();

    private StxtBlockParser() {}

    static ParsedFile parse(String text, String sourceReference, String contentChecksum) {
        Objects.requireNonNull(text, "text");
        List<OrderImportProblem> errors = new ArrayList<>();
        List<OrderImportProblem> warnings = new ArrayList<>();

        List<IndexedLine> lines = collectLines(text);
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
            return ParsedFile.empty(errors, warnings);
        }

        List<Integer> orderStarts = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            LabelValue label = parseLabel(lines.get(i).text());
            if (label != null && isOrderNumberLabel(label.label())) {
                orderStarts.add(i);
            }
        }
        if (orderStarts.isEmpty()) {
            errors.add(
                    OrderImportProblem.error(
                            CODE_NO_ORDERS,
                            "file",
                            null,
                            null,
                            "Номер заказа",
                            null,
                            "В файле не найдено ни одного блока «Номер заказа:»."));
            return ParsedFile.empty(errors, warnings);
        }

        List<OrderImportBatch> batches = new ArrayList<>();
        Map<String, Integer> seenOrderNumbers = new LinkedHashMap<>();
        for (int o = 0; o < orderStarts.size(); o++) {
            int start = orderStarts.get(o);
            int end = o + 1 < orderStarts.size() ? orderStarts.get(o + 1) : lines.size();
            OrderImportBatch batch =
                    parseOrderBlock(
                            lines.subList(start, end),
                            sourceReference,
                            contentChecksum,
                            errors,
                            warnings,
                            seenOrderNumbers);
            if (batch != null) {
                batches.add(batch);
            }
        }

        if (batches.isEmpty()) {
            return ParsedFile.empty(errors, warnings);
        }
        return new ParsedFile(batches, errors, warnings);
    }

    private static OrderImportBatch parseOrderBlock(
            List<IndexedLine> block,
            String sourceReference,
            String contentChecksum,
            List<OrderImportProblem> errors,
            List<OrderImportProblem> warnings,
            Map<String, Integer> seenOrderNumbers) {
        String orderNumber = null;
        LocalDate orderDate = null;
        LocalDate readyDate = null;
        String customerName = null;
        boolean orderDateInvalid = false;
        boolean readyDateInvalid = false;

        List<Integer> itemStarts = new ArrayList<>();
        for (int i = 0; i < block.size(); i++) {
            IndexedLine line = block.get(i);
            LabelValue label = parseLabel(line.text());
            if (label == null) {
                continue;
            }
            String normalized = normalizeLabel(label.label());
            if (isOrderNumberLabel(label.label())) {
                orderNumber = blankToNull(label.value());
            } else if (normalized.equals("дата заказа")) {
                if (isBlank(label.value())) {
                    orderDate = null;
                } else {
                    LocalDate parsed = parseDate(label.value());
                    if (parsed == null) {
                        orderDateInvalid = true;
                        errors.add(
                                fieldError(
                                        CODE_ORDER_DATE_INVALID,
                                        line.number(),
                                        "Дата заказа",
                                        label.value(),
                                        "Некорректная дата заказа."));
                    } else {
                        orderDate = parsed;
                    }
                }
            } else if (normalized.equals("дата готовности")) {
                if (!isBlank(label.value())) {
                    LocalDate parsed = parseDate(label.value());
                    if (parsed == null) {
                        readyDateInvalid = true;
                        errors.add(
                                fieldError(
                                        CODE_READY_DATE_INVALID,
                                        line.number(),
                                        "Дата готовности",
                                        label.value(),
                                        "Некорректная дата готовности."));
                    } else {
                        readyDate = parsed;
                    }
                }
            } else if (normalized.equals("клиент")) {
                customerName = blankToNull(label.value());
            } else if (isItemLabel(label.label())) {
                itemStarts.add(i);
            }
        }

        int orderLine = block.isEmpty() ? 0 : block.get(0).number();
        if (isBlank(orderNumber)) {
            errors.add(
                    fieldError(
                            CODE_ORDER_NUMBER_REQUIRED,
                            orderLine,
                            "Номер заказа",
                            orderNumber,
                            "Не задан номер заказа."));
            return null;
        }
        String trimmedOrder = orderNumber.trim();
        if (seenOrderNumbers.containsKey(trimmedOrder)) {
            errors.add(
                    fieldError(
                            CODE_DUPLICATE_ORDER_IN_FILE,
                            orderLine,
                            "Номер заказа",
                            trimmedOrder,
                            "Номер заказа «"
                                    + trimmedOrder
                                    + "» повторяется в файле."));
            return null;
        }
        seenOrderNumbers.put(trimmedOrder, orderLine);

        if (!orderDateInvalid && orderDate == null) {
            errors.add(
                    fieldError(
                            CODE_ORDER_DATE_REQUIRED,
                            orderLine,
                            "Дата заказа",
                            null,
                            "Не задана дата заказа."));
        }
        if (isBlank(customerName)) {
            errors.add(
                    fieldError(
                            CODE_CUSTOMER_REQUIRED,
                            orderLine,
                            "Клиент",
                            customerName,
                            "Не задан клиент."));
        }

        if (itemStarts.isEmpty()) {
            errors.add(
                    fieldError(
                            CODE_NO_ITEMS,
                            orderLine,
                            "Изделие",
                            null,
                            "В заказе «" + trimmedOrder + "» нет изделий."));
            return null;
        }

        List<OrderImportPosition> positions = new ArrayList<>();
        for (int i = 0; i < itemStarts.size(); i++) {
            int start = itemStarts.get(i);
            int end = i + 1 < itemStarts.size() ? itemStarts.get(i + 1) : block.size();
            OrderImportPosition position =
                    parseItemBlock(block.subList(start, end), errors, warnings);
            if (position != null) {
                positions.add(position);
            }
        }

        if (positions.isEmpty()
                || orderDateInvalid
                || readyDateInvalid
                || orderDate == null
                || isBlank(customerName)) {
            return null;
        }

        return OrderImportBatch.of(
                SOURCE_TYPE,
                sourceReference,
                contentChecksum,
                trimmedOrder,
                orderDate,
                readyDate,
                customerName.trim(),
                positions);
    }

    private static OrderImportPosition parseItemBlock(
            List<IndexedLine> block,
            List<OrderImportProblem> errors,
            List<OrderImportProblem> warnings) {
        String externalPosition = null;
        String productCode = null;
        String rawName = null;
        Integer quantity = null;
        int itemLine = block.isEmpty() ? 0 : block.get(0).number();
        int nameLabelIndex = -1;

        for (int i = 0; i < block.size(); i++) {
            IndexedLine line = block.get(i);
            LabelValue label = parseLabel(line.text());
            if (label == null) {
                continue;
            }
            String normalized = normalizeLabel(label.label());
            if (isItemLabel(label.label())) {
                externalPosition = blankToNull(label.value());
            } else if (normalized.equals("код изделия")) {
                productCode = blankToNull(label.value());
            } else if (normalized.equals("наименование изделия")) {
                nameLabelIndex = i;
                if (!isBlank(label.value())) {
                    rawName = label.value();
                }
            } else if (normalized.equals("кол-во изд.")
                    || normalized.equals("кол-во изд")
                    || normalized.equals("количество изделий")) {
                quantity = parsePositiveInteger(label.value(), line.number(), errors);
            }
        }

        if (nameLabelIndex >= 0 && rawName == null) {
            rawName = collectMultilineName(block, nameLabelIndex + 1);
        }

        String name = normalizeProductName(rawName);

        boolean failed = false;
        if (isBlank(externalPosition)) {
            errors.add(
                    fieldError(
                            CODE_EXTERNAL_POSITION_REQUIRED,
                            itemLine,
                            "Изделие",
                            externalPosition,
                            "Не задан номер изделия."));
            failed = true;
        }
        if (isBlank(productCode)) {
            errors.add(
                    fieldError(
                            CODE_PRODUCT_CODE_REQUIRED,
                            itemLine,
                            "Код изделия",
                            productCode,
                            "Не задан код изделия."));
            failed = true;
        }
        if (isBlank(name)) {
            errors.add(
                    fieldError(
                            CODE_PRODUCT_NAME_REQUIRED,
                            itemLine,
                            "Наименование изделия",
                            rawName,
                            "Не задано наименование изделия."));
            failed = true;
        }
        if (quantity == null) {
            // parsePositiveInteger already reported invalid/blank when attempted; if label missing:
            boolean hasQtyLabel = false;
            for (IndexedLine line : block) {
                LabelValue label = parseLabel(line.text());
                if (label != null) {
                    String normalized = normalizeLabel(label.label());
                    if (normalized.equals("кол-во изд.")
                            || normalized.equals("кол-во изд")
                            || normalized.equals("количество изделий")) {
                        hasQtyLabel = true;
                        break;
                    }
                }
            }
            if (!hasQtyLabel) {
                errors.add(
                        fieldError(
                                CODE_PRODUCT_QUANTITY_INVALID,
                                itemLine,
                                "Кол-во изд.",
                                null,
                                "Не задано количество изделий."));
            }
            failed = true;
        }

        int specHeaderIndex = findSpecHeaderIndex(block);
        List<OrderImportSpecificationLine> lines = new ArrayList<>();
        if (specHeaderIndex < 0) {
            errors.add(
                    fieldError(
                            CODE_SPEC_HEADER_MISSING,
                            itemLine,
                            "Артикул",
                            null,
                            "Не найден заголовок спецификации."));
            failed = true;
        } else {
            lines =
                    parseSpecificationLines(
                            block.subList(specHeaderIndex, block.size()), errors, warnings);
            if (lines.isEmpty()) {
                errors.add(
                        fieldError(
                                CODE_NO_SPEC_LINES,
                                block.get(specHeaderIndex).number(),
                                "спецификация",
                                null,
                                "Спецификация изделия пуста."));
                failed = true;
            }
        }

        if (failed) {
            return null;
        }

        return OrderImportPosition.of(
                externalPosition.trim(), productCode.trim(), name, quantity, lines);
    }

    private static String collectMultilineName(List<IndexedLine> block, int fromIndex) {
        StringBuilder builder = new StringBuilder();
        for (int i = fromIndex; i < block.size(); i++) {
            String text = block.get(i).text();
            if (isBlank(text)) {
                if (builder.length() > 0) {
                    break;
                }
                continue;
            }
            if (parseLabel(text) != null) {
                break;
            }
            if (isSpecHeaderLine(text)) {
                break;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(text.trim());
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    /**
     * Normalizes product name: collapse whitespace/newlines; drop a leading line that is only a
     * number.
     */
    static String normalizeProductName(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
        String[] parts = normalized.split("\n", -1);
        List<String> lines = new ArrayList<>();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        if (lines.isEmpty()) {
            return null;
        }
        if (lines.get(0).matches("\\d+")) {
            lines.remove(0);
        }
        if (lines.isEmpty()) {
            return null;
        }
        return String.join(" ", lines).replaceAll("\\s+", " ").trim();
    }

    private static int findSpecHeaderIndex(List<IndexedLine> block) {
        for (int i = 0; i < block.size(); i++) {
            if (isSpecHeaderLine(block.get(i).text())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isSpecHeaderLine(String text) {
        if (isBlank(text) || !text.contains(DELIMITER.trim()) && !text.contains("/")) {
            // still allow header without spaces around slash after normalize
        }
        List<String> cells = splitFields(text);
        if (cells.size() < 3) {
            return false;
        }
        int recognized = 0;
        for (String cell : cells) {
            if (HEADER_ALIASES.containsKey(normalizeHeader(cell))) {
                recognized++;
            }
        }
        return recognized >= 3 && HEADER_ALIASES.containsKey(normalizeHeader(cells.get(0)));
    }

    private static List<OrderImportSpecificationLine> parseSpecificationLines(
            List<IndexedLine> block,
            List<OrderImportProblem> errors,
            List<OrderImportProblem> warnings) {
        IndexedLine headerLine = block.get(0);
        List<String> headerCells = splitFields(headerLine.text());
        Map<SpecColumn, Integer> columnIndex = new LinkedHashMap<>();
        for (int i = 0; i < headerCells.size(); i++) {
            String rawHeader = headerCells.get(i);
            SpecColumn column = HEADER_ALIASES.get(normalizeHeader(rawHeader));
            if (column == null) {
                warnings.add(
                        OrderImportProblem.warning(
                                CODE_SPEC_HEADER_UNKNOWN,
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
            if (!columnIndex.containsKey(column)) {
                columnIndex.put(column, i);
            }
        }
        for (SpecColumn required : requiredSpecColumns()) {
            if (!columnIndex.containsKey(required)) {
                errors.add(
                        OrderImportProblem.error(
                                CODE_SPEC_HEADER_MISSING,
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
        if (!columnIndex.keySet().containsAll(requiredSpecColumns())) {
            return List.of();
        }

        List<OrderImportSpecificationLine> lines = new ArrayList<>();
        for (int i = 1; i < block.size(); i++) {
            IndexedLine line = block.get(i);
            String text = line.text();
            if (isBlank(text)) {
                continue;
            }
            if (parseLabel(text) != null) {
                break;
            }
            OrderImportSpecificationLine parsed =
                    mapSpecRow(line.number(), splitFields(text), columnIndex, errors);
            if (parsed != null) {
                lines.add(parsed);
            }
        }
        return lines;
    }

    private static OrderImportSpecificationLine mapSpecRow(
            int lineNumber,
            List<String> cells,
            Map<SpecColumn, Integer> columnIndex,
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

        String rawCode = cell(cells, columnIndex, SpecColumn.MATERIAL_CODE);
        String rawName = cell(cells, columnIndex, SpecColumn.MATERIAL_NAME);
        String rawColor = cell(cells, columnIndex, SpecColumn.COLOR);
        String rawSize = cell(cells, columnIndex, SpecColumn.SIZE);
        String rawUnit = cell(cells, columnIndex, SpecColumn.UNIT);
        String rawQty = cell(cells, columnIndex, SpecColumn.QUANTITY);

        boolean failed = false;
        if (isBlank(rawCode)) {
            errors.add(
                    fieldError(
                            CODE_MATERIAL_CODE_REQUIRED,
                            lineNumber,
                            SpecColumn.MATERIAL_CODE.displayName(),
                            rawCode,
                            "Не задан артикул."));
            failed = true;
        }
        if (isBlank(rawName)) {
            errors.add(
                    fieldError(
                            CODE_MATERIAL_NAME_REQUIRED,
                            lineNumber,
                            SpecColumn.MATERIAL_NAME.displayName(),
                            rawName,
                            "Не задано наименование."));
            failed = true;
        }
        if (isBlank(rawUnit)) {
            errors.add(
                    fieldError(
                            CODE_UNIT_REQUIRED,
                            lineNumber,
                            SpecColumn.UNIT.displayName(),
                            rawUnit,
                            "Не задана единица измерения."));
            failed = true;
        }

        BigDecimal quantity = null;
        if (isBlank(rawQty)) {
            errors.add(
                    fieldError(
                            CODE_LINE_QUANTITY_INVALID,
                            lineNumber,
                            SpecColumn.QUANTITY.displayName(),
                            rawQty,
                            "Не задано количество позиции."));
            failed = true;
        } else {
            try {
                quantity = parseDecimal(rawQty);
                if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(
                            fieldError(
                                    CODE_LINE_QUANTITY_NOT_POSITIVE,
                                    lineNumber,
                                    SpecColumn.QUANTITY.displayName(),
                                    rawQty,
                                    "Количество позиции должно быть больше нуля."));
                    failed = true;
                }
            } catch (NumberFormatException ex) {
                errors.add(
                        fieldError(
                                CODE_LINE_QUANTITY_INVALID,
                                lineNumber,
                                SpecColumn.QUANTITY.displayName(),
                                rawQty,
                                "Некорректное количество позиции."));
                failed = true;
            }
        }

        if (failed) {
            return null;
        }

        String materialCode = rawCode.trim();
        String materialName = rawName.trim();
        String color = isBlank(rawColor) ? null : rawColor.trim();
        String unit = rawUnit.trim();
        BigDecimal length = null;
        String sizeText = isBlank(rawSize) ? null : rawSize.trim();

        if (isSquareMeterUnit(unit)) {
            if (sizeText != null) {
                materialName = (materialName + " " + sizeText).replaceAll("\\s+", " ").trim();
            }
            length = null;
            unit = UNIT_PIECE_AFTER_SQ_M;
        } else if (sizeText != null) {
            LengthParse lengthParse = parseLength(sizeText);
            if (lengthParse.errorCode() != null) {
                // Dimensional text (e.g. "a x b") without кв.м. → length null, keep name as-is.
                if (!looksLikeDimensionPair(sizeText)) {
                    errors.add(
                            fieldError(
                                    lengthParse.errorCode(),
                                    lineNumber,
                                    SpecColumn.SIZE.displayName(),
                                    rawSize,
                                    lengthParse.message()));
                    return null;
                }
                length = null;
            } else {
                length = lengthParse.value();
            }
        }

        return OrderImportSpecificationLine.of(
                materialCode, materialName, color, length, unit, quantity);
    }

    private static boolean isSquareMeterUnit(String unit) {
        String normalized = unit.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
        normalized = normalized.replace(" ", "");
        return normalized.equals("кв.м.")
                || normalized.equals("кв.м")
                || normalized.equals("квм.")
                || normalized.equals("квм")
                || normalized.equals("м2")
                || normalized.equals("м²");
    }

    private static boolean looksLikeDimensionPair(String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        return value.contains(" x ") || value.contains(" х ") || value.contains("×");
    }

    private static Integer parsePositiveInteger(
            String raw, int lineNumber, List<OrderImportProblem> errors) {
        if (isBlank(raw)) {
            errors.add(
                    fieldError(
                            CODE_PRODUCT_QUANTITY_INVALID,
                            lineNumber,
                            "Кол-во изд.",
                            raw,
                            "Не задано количество изделий."));
            return null;
        }
        try {
            Integer value = parseInteger(raw);
            if (value <= 0) {
                errors.add(
                        fieldError(
                                CODE_PRODUCT_QUANTITY_NOT_POSITIVE,
                                lineNumber,
                                "Кол-во изд.",
                                raw,
                                "Количество изделий должно быть больше нуля."));
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            errors.add(
                    fieldError(
                            CODE_PRODUCT_QUANTITY_INVALID,
                            lineNumber,
                            "Кол-во изд.",
                            raw,
                            "Некорректное количество изделий."));
            return null;
        }
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

    static LengthParse parseLength(String raw) {
        if (isBlank(raw)) {
            return LengthParse.ok(null);
        }
        if (looksLikeDimensionPair(raw)) {
            return LengthParse.error(
                    CODE_LENGTH_INVALID, "Размер в виде габаритов не является длиной в мм.");
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
            BigDecimal value = new BigDecimal(normalized);
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                return LengthParse.error(
                        CODE_LENGTH_INVALID, "Размер должен быть больше нуля либо пустым.");
            }
            return LengthParse.ok(value);
        } catch (NumberFormatException ex) {
            return LengthParse.error(
                    CODE_LENGTH_INVALID, "Некорректный размер. Ожидается число в миллиметрах.");
        }
    }

    /** Compatibility alias used by tests. */
    static LengthParse parseLengthMm(String raw) {
        return parseLength(raw);
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

    private static LocalDate parseDate(String raw) {
        String value = raw.trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    private static String cell(
            List<String> cells, Map<SpecColumn, Integer> columnIndex, SpecColumn column) {
        Integer index = columnIndex.get(column);
        if (index == null || index < 0 || index >= cells.size()) {
            return null;
        }
        return cells.get(index);
    }

    private static LabelValue parseLabel(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = LABEL_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        String label = matcher.group(1).trim();
        String value = matcher.group(2) == null ? "" : matcher.group(2).trim();
        if (label.isEmpty()) {
            return null;
        }
        // Spec header lines contain " / " and are not labels even if a colon appears.
        if (text.contains(DELIMITER)) {
            return null;
        }
        return new LabelValue(label, value);
    }

    private static boolean isOrderNumberLabel(String label) {
        return normalizeLabel(label).equals("номер заказа");
    }

    private static boolean isItemLabel(String label) {
        String normalized = normalizeLabel(label);
        return normalized.equals("изделие");
    }

    private static String normalizeLabel(String label) {
        String value = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        value = value.replace('ё', 'е');
        value = value.replaceAll("\\s+", " ");
        return value;
    }

    static String normalizeHeader(String header) {
        String value = header == null ? "" : header.trim().toLowerCase(Locale.ROOT);
        value = value.replace('ё', 'е');
        value = value.replaceAll("\\s+", " ");
        return value;
    }

    private static List<SpecColumn> requiredSpecColumns() {
        return List.of(
                SpecColumn.MATERIAL_CODE,
                SpecColumn.MATERIAL_NAME,
                SpecColumn.COLOR,
                SpecColumn.SIZE,
                SpecColumn.UNIT,
                SpecColumn.QUANTITY);
    }

    private static Map<String, SpecColumn> buildHeaderAliases() {
        Map<String, SpecColumn> map = new LinkedHashMap<>();
        putAliases(map, SpecColumn.MATERIAL_CODE, "Артикул", "Код материала");
        putAliases(map, SpecColumn.MATERIAL_NAME, "Наименование", "Название");
        putAliases(map, SpecColumn.COLOR, "Цвет");
        putAliases(map, SpecColumn.SIZE, "Размер", "Размер, мм", "Длина", "Длина, мм");
        putAliases(
                map,
                SpecColumn.UNIT,
                "Единица измерения",
                "Ед. изм.",
                "Ед.изм.",
                "ЕИ");
        putAliases(
                map,
                SpecColumn.QUANTITY,
                "Кол-во позиции на 1 изделие",
                "Кол-во позиции",
                "Количество позиции",
                "Количество");
        return Map.copyOf(map);
    }

    private static void putAliases(
            Map<String, SpecColumn> map, SpecColumn column, String... aliases) {
        for (String alias : aliases) {
            map.put(normalizeHeader(alias), column);
        }
    }

    private static List<IndexedLine> collectLines(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] parts = normalized.split("\n", -1);
        List<IndexedLine> lines = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            String line = parts[i];
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("@") || trimmed.startsWith("#")) {
                continue;
            }
            lines.add(new IndexedLine(i + 1, line));
        }
        return lines;
    }

    private static OrderImportProblem fieldError(
            String code, int lineNumber, String field, String rawValue, String message) {
        return OrderImportProblem.error(
                code, rowLocation(lineNumber), null, null, field, rawValue, message);
    }

    private static String rowLocation(int lineNumber) {
        return "строка " + lineNumber;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private enum SpecColumn {
        MATERIAL_CODE("Артикул"),
        MATERIAL_NAME("Наименование"),
        COLOR("Цвет"),
        SIZE("Размер"),
        UNIT("Единица измерения"),
        QUANTITY("Кол-во позиции на 1 изделие");

        private final String displayName;

        SpecColumn(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }
    }

    record ParsedFile(
            List<OrderImportBatch> batches,
            List<OrderImportProblem> errors,
            List<OrderImportProblem> warnings) {

        static ParsedFile empty(
                List<OrderImportProblem> errors, List<OrderImportProblem> warnings) {
            return new ParsedFile(List.of(), errors, warnings);
        }
    }

    private record IndexedLine(int number, String text) {}

    private record LabelValue(String label, String value) {}

    record LengthParse(BigDecimal value, String errorCode, String message) {
        static LengthParse ok(BigDecimal value) {
            return new LengthParse(value, null, null);
        }

        static LengthParse error(String code, String message) {
            return new LengthParse(null, code, message);
        }
    }
}
