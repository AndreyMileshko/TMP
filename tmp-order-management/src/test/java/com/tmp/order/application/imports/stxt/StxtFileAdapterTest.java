package com.tmp.order.application.imports.stxt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportPosition;
import com.tmp.order.api.imports.OrderImportProblem;
import com.tmp.order.api.imports.OrderImportSpecificationLine;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StxtFileAdapterTest {

    private static final Charset WINDOWS_1251 = Charset.forName("Windows-1251");

    private final StxtFileAdapter adapter = new StxtFileAdapter();

    @Test
    void parsesMultipleOrdersInOneFile() throws IOException {
        StxtParseResult result =
                adapter.parse(readFixture("stxt/multi-order.stxt"), "multi-order.stxt");

        assertTrue(result.isSuccessful(), () -> result.errors().toString());
        assertEquals(2, result.batches().size());
        assertEquals("25096190", result.batches().get(0).orderNumber());
        assertEquals("25096053", result.batches().get(1).orderNumber());
    }

    @Test
    void parsesMultipleItemsInOneOrder() throws IOException {
        OrderImportBatch batch = parseSampleBatch();

        assertEquals(2, batch.positionCount());
        assertEquals("1", batch.positions().get(0).externalPositionNumber());
        assertEquals("2", batch.positions().get(1).externalPositionNumber());
    }

    @Test
    void parsesMultipleSpecificationLines() throws IOException {
        OrderImportBatch batch = parseSampleBatch();

        assertEquals(3, batch.positions().get(0).specificationLines().size());
        assertEquals(1, batch.positions().get(1).specificationLines().size());
        assertEquals(4, batch.specificationLineCount());
    }

    @Test
    void normalizesProductNameRemovingLeadingNumberOnlyLine() throws IOException {
        OrderImportBatch batch = parseSampleBatch();
        OrderImportPosition whs =
                batch.positions().stream()
                        .filter(position -> "WHS_60".equals(position.productCode()))
                        .findFirst()
                        .orElseThrow();

        assertEquals("WHS HALO WHS_60 ActivPilot", whs.name());
    }

    @Test
    void skipsAtCommandLines() throws IOException {
        OrderImportBatch batch = parseSampleBatch();
        List<String> materialCodes =
                batch.positions().get(0).specificationLines().stream()
                        .map(OrderImportSpecificationLine::materialCode)
                        .toList();

        assertEquals(List.of("107.225белый", "108.100", "200.001"), materialCodes);
    }

    @Test
    void skipsHashCommentLines() throws IOException {
        OrderImportBatch batch = parseSampleBatch();

        assertFalse(
                batch.positions().get(0).specificationLines().stream()
                        .anyMatch(line -> line.materialCode().contains("#")));
    }

    @Test
    void squareMeterUnitAppendsSizeToNameAndUsesPieceUnit() throws IOException {
        OrderImportBatch batch = parseSampleBatch();
        OrderImportSpecificationLine sqm =
                batch.positions().get(0).specificationLines().stream()
                        .filter(line -> "200.001".equals(line.materialCode()))
                        .findFirst()
                        .orElseThrow();

        assertNull(sqm.lengthMm());
        assertEquals(StxtBlockParser.UNIT_PIECE_AFTER_SQ_M, sqm.unitOfMeasure());
        assertTrue(sqm.materialName().contains("596,0 x 976,0мм."));
    }

    @Test
    void doesNotMultiplyLineQuantityByProductQuantity() throws IOException {
        OrderImportBatch batch = parseSampleBatch();
        OrderImportSpecificationLine line = batch.positions().get(0).specificationLines().get(0);

        assertEquals(8, batch.positions().get(0).productQuantity());
        assertEquals(0, new BigDecimal("16").compareTo(line.lineQuantity()));
    }

    @Test
    void parsesUtf8SampleWithCommercialHeaderAndSpecDetails() throws IOException {
        OrderImportBatch batch = parseSampleBatch();

        assertEquals("26062891", batch.orderNumber());
        assertEquals(LocalDate.of(2026, 6, 25), batch.orderDate());
        assertEquals(LocalDate.of(2026, 7, 1), batch.readyDate());
        assertEquals("Альпы ООО", batch.customerName());
        assertEquals("WHS_60", batch.positions().get(0).productCode());

        OrderImportSpecificationLine firstLine = batch.positions().get(0).specificationLines().get(0);
        assertEquals("107.225белый", firstLine.materialCode());
        assertEquals("Штапик черный 8 мм/38.39.40", firstLine.materialName());
        assertEquals("Белый", firstLine.color());
        assertEquals(0, new BigDecimal("2066.0").compareTo(firstLine.lengthMm()));
        assertEquals("шт.", firstLine.unitOfMeasure());
    }

    @Test
    void parsesWindows1251() {
        String content =
                """
                Номер заказа: 26062891
                Дата заказа: 25.06.2026
                Клиент: Альпы ООО

                Изделие: 1
                Код изделия: WHS_60
                Наименование изделия: Штапик черный 8 мм/38.39.40
                Кол-во изд.: 1

                Артикул / Наименование / Цвет / Размер / Единица измерения / Кол-во позиции на 1 изделие
                107.225белый / Штапик черный 8 мм/38.39.40 / Белый / 2066,0мм. / шт. / 16
                """;
        StxtParseResult result = adapter.parse(content.getBytes(WINDOWS_1251), "cp1251.stxt");

        assertTrue(result.isSuccessful());
        assertEquals(StxtEncodingDetector.NAME_WINDOWS_1251, result.detectedEncoding().orElseThrow());
        assertEquals(
                "Штапик черный 8 мм/38.39.40",
                result.batch().orElseThrow().positions().get(0).name());
    }

    @Test
    void parsesUtf8Bom() {
        String body =
                """
                Номер заказа: 26062891
                Дата заказа: 25.06.2026
                Клиент: Клиент

                Изделие: 1
                Код изделия: A1
                Наименование изделия: Деталь
                Кол-во изд.: 2

                Артикул / Наименование / Цвет / Размер / Единица измерения / Кол-во позиции на 1 изделие
                A1 / Деталь / Белый / 100мм / шт / 2
                """;
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
    void trailingExtraSpecFieldsAreIgnoredWithFixedLayout() {
        String content =
                """
                Номер заказа: ORD-1
                Дата заказа: 25.06.2026
                Клиент: Клиент

                Изделие: 1
                Код изделия: P-1
                Наименование изделия: Изделие
                Кол-во изд.: 1

                Артикул / Наименование / Цвет / Размер / Единица измерения / Кол-во позиции на 1 изделие
                CODE / Материал /  / 12,5мм. / шт / 5 / X
                """;
        StxtParseResult result = adapter.parse(content.getBytes(StandardCharsets.UTF_8), "extra.stxt");

        assertTrue(result.isSuccessful(), () -> result.errors().toString());
        assertEquals(1, result.batch().orElseThrow().specificationLineCount());
        assertEquals(
                0,
                new BigDecimal("5")
                        .compareTo(
                                result.batch()
                                        .orElseThrow()
                                        .positions()
                                        .get(0)
                                        .specificationLines()
                                        .get(0)
                                        .quantity()));
    }

    @Test
    void incompleteSpecRowWithoutSixFieldsIsError() {
        String content =
                """
                Номер заказа: 26062891
                Дата заказа: 25.06.2026
                Клиент: Клиент

                Изделие: 1
                Код изделия: P-1
                Наименование изделия: Изделие
                Кол-во изд.: 1

                CODE / Name / Белый / 1
                """;
        StxtParseResult result = adapter.parse(content.getBytes(StandardCharsets.UTF_8), "short-row.stxt");

        assertTrue(result.hasErrors());
        assertTrue(result.batches().isEmpty());
        assertTrue(
                result.errors().stream()
                        .anyMatch(p -> StxtBlockParser.CODE_COLUMN_COUNT.equals(p.code())
                                || StxtBlockParser.CODE_NO_SPEC_LINES.equals(p.code())));
    }

    @Test
    void repeatedOrderHeaderMergesItemsIntoOneBatch() {
        String content =
                """
                Номер заказа: 25096190
                Дата заказа: 23.09.2025
                Дата готовности: 14.10.2025
                Клиент: Парус ООО
                Изделие: 2
                Код изделия: 25096190/2
                Наименование изделия: 1
                WHS HALO
                WHS_60
                ActivPilot
                Кол-во изд.: 1
                Артикул / Наименование / Цвет / Размер / Единица измерения / Кол-во позиции на 1 изделие
                101315белый / Рама 58/60 WHS60 / Белый / 796,0мм. / м. / 2

                Номер заказа: 25096190
                Дата заказа: 23.09.2025
                Дата готовности: 14.10.2025
                Клиент: Парус ООО
                Изделие: 3
                Код изделия: 25096190/3
                Наименование изделия: 1
                WHS HALO
                WHS_60
                ActivPilot
                Кол-во изд.: 1
                Артикул / Наименование / Цвет / Размер / Единица измерения / Кол-во позиции на 1 изделие
                200.001 / Стеклопакет / Белый / 606,0 x 996,0мм. / кв.м. / 1
                """;
        StxtParseResult result =
                adapter.parse(content.getBytes(StandardCharsets.UTF_8), "repeated-header.stxt");

        assertTrue(result.isSuccessful(), () -> result.errors().toString());
        assertEquals(1, result.batches().size());
        OrderImportBatch batch = result.batches().get(0);
        assertEquals("25096190", batch.orderNumber());
        assertEquals(2, batch.positionCount());
        assertEquals("2", batch.positions().get(0).externalPositionNumber());
        assertEquals("3", batch.positions().get(1).externalPositionNumber());
        assertEquals("WHS HALO WHS_60 ActivPilot", batch.positions().get(0).name());
        assertEquals("шт.", batch.positions().get(1).specificationLines().get(0).unitOfMeasure());
    }

    @Test
    void finalExportFormatFixtureParsesWithOrdersItemsAndZeroErrors() throws IOException {
        StxtParseResult result =
                adapter.parse(readFixture("stxt/final-export-format.stxt"), "final-export-format.stxt");

        assertTrue(result.isSuccessful(), () -> result.errors().toString());
        assertTrue(result.batches().size() > 0, "orders > 0");
        int positions =
                result.batches().stream().mapToInt(OrderImportBatch::positionCount).sum();
        assertTrue(positions > 0, "positions > 0");
        int productQty =
                result.batches().stream()
                        .flatMap(batch -> batch.positions().stream())
                        .mapToInt(OrderImportPosition::quantity)
                        .sum();
        assertTrue(productQty > 0, "product quantities > 0");
        assertEquals(0, result.errors().size());
        assertTrue(
                result.batches().stream()
                        .noneMatch(batch -> "UNKNOWN".equalsIgnoreCase(batch.orderNumber())));
    }

    @Test
    void invalidProductQuantityIsError() {
        String content =
                """
                Номер заказа: 26062891
                Дата заказа: 25.06.2026
                Клиент: Клиент

                Изделие: 1
                Код изделия: P-1
                Наименование изделия: Изделие
                Кол-во изд.: 0

                Артикул / Наименование / Цвет / Размер / Единица измерения / Кол-во позиции на 1 изделие
                CODE / Name / Белый / 10мм / шт / 1
                """;
        StxtParseResult result = adapter.parse(content.getBytes(StandardCharsets.UTF_8), "bad-qty.stxt");

        assertTrue(result.hasErrors());
        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                p ->
                                        StxtBlockParser.CODE_PRODUCT_QUANTITY_NOT_POSITIVE.equals(
                                                p.code())));
        assertProblemHasRowColumnValue(result.errors().get(0));
    }

    @Test
    void invalidLineQuantityIsError() {
        String content =
                """
                Номер заказа: 26062891
                Дата заказа: 25.06.2026
                Клиент: Клиент

                Изделие: 1
                Код изделия: P-1
                Наименование изделия: Изделие
                Кол-во изд.: 1

                Артикул / Наименование / Цвет / Размер / Единица измерения / Кол-во позиции на 1 изделие
                CODE / Name / Белый / 10мм / шт / -2
                """;
        StxtParseResult result = adapter.parse(content.getBytes(StandardCharsets.UTF_8), "bad-line.stxt");

        assertTrue(result.hasErrors());
        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                p ->
                                        StxtBlockParser.CODE_LINE_QUANTITY_NOT_POSITIVE.equals(
                                                p.code())));
    }

    @Test
    void emptyFileIsError() {
        StxtParseResult result = adapter.parse(new byte[0], "empty.stxt");
        assertTrue(result.hasErrors());
        assertEquals(StxtBlockParser.CODE_FILE_EMPTY, result.errors().get(0).code());
        assertUserSafe(result.errors().get(0));
    }

    @Test
    void splitFieldsKeepsSlashInsideName() {
        List<String> fields =
                StxtBlockParser.splitFields(
                        "26062891 / 1 / 8 / 107.225белый / Штапик черный 8 мм/38.39.40 / Белый / 2066,0мм. / 16");
        assertEquals(8, fields.size());
        assertEquals("Штапик черный 8 мм/38.39.40", fields.get(4));
    }

    @Test
    void lengthParserNormalizesMmAndComma() {
        assertEquals(
                0,
                new BigDecimal("2066.0")
                        .compareTo(StxtBlockParser.parseLengthMm("2066,0мм.").value()));
        assertEquals(
                0,
                new BigDecimal("555.5").compareTo(StxtBlockParser.parseLengthMm("555,5мм.").value()));
        assertNull(StxtBlockParser.parseLengthMm("  ").value());
        assertNull(StxtBlockParser.parseLengthMm(null).value());
        assertNotNull(StxtBlockParser.parseLengthMm("abc").errorCode());
    }

    private OrderImportBatch parseSampleBatch() throws IOException {
        StxtParseResult result =
                adapter.parse(readFixture("stxt/sample-utf8.stxt"), "sample-utf8.stxt");
        assertTrue(result.isSuccessful(), () -> result.errors().toString());
        return result.batch().orElseThrow();
    }

    private static byte[] readFixture(String classpath) throws IOException {
        try (InputStream in = StxtFileAdapterTest.class.getClassLoader().getResourceAsStream(classpath)) {
            if (in == null) {
                throw new IOException("Missing fixture: " + classpath);
            }
            return in.readAllBytes();
        }
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
