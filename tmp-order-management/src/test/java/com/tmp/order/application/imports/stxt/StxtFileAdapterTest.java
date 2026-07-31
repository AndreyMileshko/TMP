package com.tmp.order.application.imports.stxt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportPosition;
import com.tmp.order.api.imports.OrderImportProblem;
import com.tmp.order.api.imports.OrderImportProblemSeverity;
import com.tmp.order.api.imports.OrderImportSpecificationLine;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class StxtFileAdapterTest {

    private static final Charset WINDOWS_1251 = Charset.forName("Windows-1251");
    private static final String HEADER =
            "Счет / Изделие / Кол-во изд. / Артикул / Наименование / Цвет / Размер / Кол-во позиции";

    private final StxtFileAdapter adapter = new StxtFileAdapter();

    @Test
    void parsesUtf8SampleWithSlashInsideNameAndDecimalComma() {
        String content =
                HEADER
                        + "\n"
                        + "26062891 / 1 / 8 / 107.225белый / Штапик черный 8 мм/38.39.40 / Белый / 2066,0мм. / 16\n";
        StxtParseResult result = adapter.parse(content.getBytes(StandardCharsets.UTF_8), "sample.stxt");

        assertTrue(result.isSuccessful());
        assertEquals(StxtEncodingDetector.NAME_UTF8, result.detectedEncoding().orElseThrow());
        OrderImportBatch batch = result.batch().orElseThrow();
        assertEquals("STXT", batch.sourceType());
        assertEquals("sample.stxt", batch.sourceReference());
        assertEquals("26062891", batch.orderNumber());
        assertEquals(1, batch.positionCount());

        OrderImportPosition position = batch.positions().get(0);
        assertEquals("1", position.externalPositionNumber());
        assertEquals(8, position.productQuantity());
        OrderImportSpecificationLine line = position.specificationLines().get(0);
        assertEquals("107.225белый", line.materialCode());
        assertEquals("Штапик черный 8 мм/38.39.40", line.materialName());
        assertEquals("Белый", line.color());
        assertEquals(0, new BigDecimal("2066.0").compareTo(line.lengthMm()));
        assertEquals(0, new BigDecimal("16").compareTo(line.lineQuantity()));
    }

    @Test
    void parsesWindows1251() {
        String content =
                HEADER
                        + "\n"
                        + "26062891 / 1 / 8 / 107.225белый / Штапик черный 8 мм/38.39.40 / Белый / 2066,0мм. / 16\n";
        StxtParseResult result = adapter.parse(content.getBytes(WINDOWS_1251), "cp1251.stxt");

        assertTrue(result.isSuccessful());
        assertEquals(StxtEncodingDetector.NAME_WINDOWS_1251, result.detectedEncoding().orElseThrow());
        assertEquals(
                "Штапик черный 8 мм/38.39.40",
                result.batch().orElseThrow().positions().get(0).specificationLines().get(0).materialName());
    }

    @Test
    void parsesUtf8Bom() {
        String body =
                HEADER
                        + "\n"
                        + "26062891 / 1 / 8 / A1 / Деталь / Белый / 100мм / 2\n";
        byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] text = body.getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + text.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(text, 0, content, bom.length, text.length);

        StxtParseResult result = adapter.parse(content, "bom.stxt");
        assertTrue(result.isSuccessful());
        assertEquals(StxtEncodingDetector.NAME_UTF8_BOM, result.detectedEncoding().orElseThrow());
        assertEquals("26062891", result.batch().orElseThrow().orderNumber());
    }

    @Test
    void ignoresEmptyLines() {
        String content =
                HEADER
                        + "\n\n"
                        + "26062891 / 1 / 1 / A / Name /  / 10мм / 1\n"
                        + "\n"
                        + "26062891 / 1 / 1 / B / Other /  /  / 2\n\n";
        StxtParseResult result = adapter.parse(content.getBytes(StandardCharsets.UTF_8), "empty-lines.stxt");
        assertTrue(result.isSuccessful());
        assertEquals(1, result.batch().orElseThrow().positionCount());
        assertEquals(2, result.batch().orElseThrow().specificationLineCount());
    }

    @Test
    void blankColorAndBlankLengthBecomeNull() {
        String content =
                HEADER
                        + "\n"
                        + "26062891 / 1 / 1 / A / Name /  /  / 1\n";
        StxtParseResult result = adapter.parse(content.getBytes(StandardCharsets.UTF_8), "nulls.stxt");
        assertTrue(result.isSuccessful());
        OrderImportSpecificationLine line =
                result.batch().orElseThrow().positions().get(0).specificationLines().get(0);
        assertNull(line.color());
        assertNull(line.lengthMm());
        assertEquals(0, BigDecimal.ONE.compareTo(line.lineQuantity()));
    }

    @Test
    void doesNotMultiplyLineQuantityByProductQuantity() {
        String content =
                HEADER
                        + "\n"
                        + "26062891 / 1 / 8 / A / Name / Белый / 100мм / 16\n";
        StxtParseResult result = adapter.parse(content.getBytes(StandardCharsets.UTF_8), "qty.stxt");
        assertEquals(
                0,
                new BigDecimal("16")
                        .compareTo(
                                result.batch()
                                        .orElseThrow()
                                        .positions()
                                        .get(0)
                                        .specificationLines()
                                        .get(0)
                                        .lineQuantity()));
    }

    @Test
    void acceptsHeaderAliases() {
        String content =
                "Счёт / Позиция / Количество изделий / Код материала / Название / Цвет / Длина / Количество\n"
                        + "ORD-1 / 10 / 3 / CODE / Материал /  / 12,5мм. / 5\n";
        StxtParseResult result = adapter.parse(content.getBytes(StandardCharsets.UTF_8), "aliases.stxt");
        assertTrue(result.isSuccessful());
        OrderImportBatch batch = result.batch().orElseThrow();
        assertEquals("ORD-1", batch.orderNumber());
        assertEquals("10", batch.positions().get(0).externalPositionNumber());
        assertEquals(3, batch.positions().get(0).productQuantity());
        assertEquals(
                0,
                new BigDecimal("12.5")
                        .compareTo(batch.positions().get(0).specificationLines().get(0).lengthMm()));
    }

    @Test
    void unknownHeaderIsWarningNotDomainField() {
        String content =
                "Счет / Изделие / Кол-во изд. / Артикул / Наименование / Цвет / Размер / Кол-во позиции / Лишнее\n"
                        + "26062891 / 1 / 1 / A / Name / Белый / 10мм / 1 / X\n";
        StxtParseResult result = adapter.parse(content.getBytes(StandardCharsets.UTF_8), "unknown.stxt");
        assertTrue(result.isSuccessful());
        assertFalse(result.warnings().isEmpty());
        assertTrue(
                result.warnings().stream()
                        .anyMatch(p -> StxtTableParser.CODE_HEADER_UNKNOWN.equals(p.code())));
        assertEquals(OrderImportProblemSeverity.WARNING, result.warnings().get(0).severity());
    }

    @Test
    void missingRequiredHeaderIsError() {
        String content =
                "Счет / Изделие / Кол-во изд. / Артикул / Наименование / Цвет / Кол-во позиции\n"
                        + "26062891 / 1 / 1 / A / Name / Белый / 1\n";
        StxtParseResult result = adapter.parse(content.getBytes(StandardCharsets.UTF_8), "headers.stxt");
        assertTrue(result.hasErrors());
        assertTrue(result.batch().isEmpty());
        assertTrue(
                result.errors().stream()
                        .anyMatch(p -> StxtTableParser.CODE_HEADER_MISSING.equals(p.code())));
    }

    @Test
    void multipleOrdersRejectedWithoutBatch() {
        String content =
                HEADER
                        + "\n"
                        + "26062891 / 1 / 1 / A / Name / Белый / 10мм / 1\n"
                        + "99999999 / 2 / 1 / B / Other / Белый / 10мм / 1\n";
        StxtParseResult result = adapter.parse(content.getBytes(StandardCharsets.UTF_8), "multi.stxt");
        assertTrue(result.hasErrors());
        assertTrue(result.batch().isEmpty());
        assertTrue(
                result.errors().stream()
                        .anyMatch(p -> StxtTableParser.CODE_MULTIPLE_ORDERS.equals(p.code())));
    }

    @Test
    void invalidProductQuantityIsError() {
        String content =
                HEADER
                        + "\n"
                        + "26062891 / 1 / 0 / A / Name / Белый / 10мм / 1\n";
        StxtParseResult result = adapter.parse(content.getBytes(StandardCharsets.UTF_8), "bad-qty.stxt");
        assertTrue(result.hasErrors());
        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                p ->
                                        StxtTableParser.CODE_PRODUCT_QUANTITY_NOT_POSITIVE.equals(
                                                p.code())));
        assertProblemHasRowColumnValue(result.errors().get(0));
    }

    @Test
    void invalidLineQuantityIsError() {
        String content =
                HEADER
                        + "\n"
                        + "26062891 / 1 / 1 / A / Name / Белый / 10мм / -2\n";
        StxtParseResult result = adapter.parse(content.getBytes(StandardCharsets.UTF_8), "bad-line.stxt");
        assertTrue(result.hasErrors());
        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                p ->
                                        StxtTableParser.CODE_LINE_QUANTITY_NOT_POSITIVE.equals(
                                                p.code())));
    }

    @Test
    void emptyFileIsError() {
        StxtParseResult result = adapter.parse(new byte[0], "empty.stxt");
        assertTrue(result.hasErrors());
        assertEquals(StxtTableParser.CODE_FILE_EMPTY, result.errors().get(0).code());
        assertUserSafe(result.errors().get(0));
    }

    @Test
    void splitFieldsKeepsSlashInsideName() {
        List<String> fields =
                StxtTableParser.splitFields(
                        "26062891 / 1 / 8 / 107.225белый / Штапик черный 8 мм/38.39.40 / Белый / 2066,0мм. / 16");
        assertEquals(8, fields.size());
        assertEquals("Штапик черный 8 мм/38.39.40", fields.get(4));
    }

    @Test
    void lengthParserNormalizesMmAndComma() {
        assertEquals(
                0,
                new BigDecimal("2066.0")
                        .compareTo(StxtTableParser.parseLengthMm("2066,0мм.").value()));
        assertEquals(
                0,
                new BigDecimal("555.5").compareTo(StxtTableParser.parseLengthMm("555,5мм.").value()));
        assertNull(StxtTableParser.parseLengthMm("  ").value());
        assertNull(StxtTableParser.parseLengthMm(null).value());
        assertNotNull(StxtTableParser.parseLengthMm("abc").errorCode());
    }

    private static void assertProblemHasRowColumnValue(OrderImportProblem problem) {
        assertNotNull(problem.location());
        assertNotNull(problem.fieldName());
        assertNotNull(problem.message());
        assertUserSafe(problem);
    }

    private static void assertUserSafe(OrderImportProblem problem) {
        String message = problem.message().toLowerCase();
        assertFalse(message.contains("sql"));
        assertFalse(message.contains("exception"));
        assertFalse(message.contains("stack"));
        assertFalse(problem.message().contains("\tat "));
    }
}
