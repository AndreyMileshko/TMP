package com.tmp.ui.shell.screen.orderimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportConfirmResult;
import com.tmp.order.api.imports.OrderImportConflictException;
import com.tmp.order.api.imports.OrderImportFileParseResult;
import com.tmp.order.api.imports.OrderImportPreview;
import com.tmp.order.api.imports.OrderImportProblem;
import com.tmp.order.api.imports.OrderImportService;
import com.tmp.order.api.imports.OrderImportValidationException;
import com.tmp.order.api.imports.PreparedOrderImportPlan;
import com.tmp.order.api.imports.StxtOrderFileParser;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.order.error.OrderUiErrorMapper;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OrderImportViewModelTest {

    @Test
    void selectFileSetsNameAndRunsPreview() {
        FakeStxtParser stxt = new FakeStxtParser();
        FakeImportService imports = new FakeImportService();
        imports.preview = successPreview("ORD-1", 2, "3", 4);
        OrderImportViewModel viewModel =
                new OrderImportViewModel(imports, stxt, createAuth());

        viewModel.selectFile(Path.of("sample.stxt"));

        assertEquals("sample.stxt", viewModel.fileNameProperty().get());
        assertEquals(1, stxt.parseCalls.get());
        assertEquals(1, imports.previewCalls.get());
        assertEquals("Заказ: ORD-1", viewModel.previewOrdersTextProperty().get());
        assertEquals("", viewModel.previewOrderNumbersTextProperty().get());
        assertEquals("2", viewModel.previewPositionCountProperty().get());
        assertEquals("3", viewModel.previewProductQuantityProperty().get());
        assertEquals("4", viewModel.previewSpecificationLineCountProperty().get());
        assertEquals("Ошибки: 0", viewModel.previewErrorCountTextProperty().get());
        assertEquals("Предупреждения: 0", viewModel.previewWarningCountTextProperty().get());
        assertTrue(viewModel.canImportProperty().get());
        assertEquals(OrderImportViewModel.MSG_PREVIEW_OK, viewModel.previewStatusTextProperty().get());
        assertEquals("Импортировать заказ №ORD-1?", viewModel.confirmationTitle());
        assertEquals("Импортировать заказ", viewModel.confirmationConfirmLabel());
    }

    @Test
    void loadingClearsAfterPreview() {
        FakeImportService imports = new FakeImportService();
        imports.preview = successPreview("ORD-L", 1, "1", 1);
        OrderImportViewModel viewModel =
                new OrderImportViewModel(imports, new FakeStxtParser(), createAuth());
        viewModel.selectFile(Path.of("a.stxt"));
        assertFalse(viewModel.loadingProperty().get());
    }

    @Test
    void previewErrorDisablesImportAndShowsCounters() {
        FakeStxtParser stxt = new FakeStxtParser();
        stxt.result = OrderImportFileParseResult.of(
                List.of(),
                List.of(OrderImportProblem.error(
                        "MISSING_COLUMN",
                        "header",
                        null,
                        null,
                        "Артикул",
                        null,
                        "Файл не содержит обязательную колонку Артикул")),
                List.of(),
                null);
        OrderImportViewModel viewModel =
                new OrderImportViewModel(new FakeImportService(), stxt, createAuth());

        viewModel.selectFile(Path.of("bad.stxt"));

        assertFalse(viewModel.canImportProperty().get());
        assertNull(viewModel.preparedPlanForTest());
        assertFalse(viewModel.previewSucceededWithoutErrorsForTest());
        assertEquals(1, viewModel.problems().size());
        assertEquals(1, viewModel.errors().size());
        assertEquals("Ошибки: 1", viewModel.previewErrorCountTextProperty().get());
        assertEquals("Предупреждения: 0", viewModel.previewWarningCountTextProperty().get());
        assertEquals(
                OrderImportViewModel.MSG_PREVIEW_ERRORS, viewModel.previewStatusTextProperty().get());
        assertEquals(
                "Файл не содержит обязательную колонку Артикул",
                viewModel.problems().get(0).message());
        assertTrue(viewModel.problemsTableVisibleProperty().get());
        assertTrue(viewModel.errorMessageProperty().get().isBlank());
    }

    @Test
    void previewSuccessEnablesImport() {
        FakeImportService imports = new FakeImportService();
        imports.preview = successPreview("ORD-OK", 1, "1", 1);
        OrderImportViewModel viewModel =
                new OrderImportViewModel(imports, new FakeStxtParser(), createAuth());

        viewModel.selectFile(Path.of("ok.stxt"));

        assertTrue(viewModel.canImportProperty().get());
        assertNotNull(viewModel.preparedPlanForTest());
        assertTrue(viewModel.previewSucceededWithoutErrorsForTest());
        assertEquals("Ошибки: 0", viewModel.previewErrorCountTextProperty().get());
        assertEquals(OrderImportViewModel.MSG_PREVIEW_OK, viewModel.previewStatusTextProperty().get());
    }

    @Test
    void noProblemsShowsEmptyMessageAndHidesTable() {
        FakeImportService imports = new FakeImportService();
        imports.preview = successPreview("ORD-OK", 1, "1", 1);
        OrderImportViewModel viewModel =
                new OrderImportViewModel(imports, new FakeStxtParser(), createAuth());

        viewModel.selectFile(Path.of("ok.stxt"));

        assertTrue(viewModel.problems().isEmpty());
        assertEquals(
                OrderImportViewModel.MSG_NO_PROBLEMS, viewModel.problemsEmptyTextProperty().get());
        assertFalse(viewModel.problemsTableVisibleProperty().get());
        assertTrue(viewModel.canImportProperty().get());
    }

    @Test
    void warningStateAllowsImportAndShowsWarningCounter() {
        FakeImportService imports = new FakeImportService();
        imports.preview = OrderImportPreview.of(
                "file.stxt",
                "ORD-W",
                1,
                new BigDecimal("1"),
                1,
                List.of(),
                List.of(OrderImportProblem.warning(
                        "UNKNOWN_HEADER",
                        "header",
                        null,
                        null,
                        "Extra",
                        null,
                        "Неизвестная колонка Extra")),
                new FakePlan("ORD-W"));
        OrderImportViewModel viewModel =
                new OrderImportViewModel(imports, new FakeStxtParser(), createAuth());

        viewModel.selectFile(Path.of("warn.stxt"));

        assertTrue(viewModel.canImportProperty().get());
        assertEquals(1, viewModel.warnings().size());
        assertEquals(1, viewModel.problems().size());
        assertEquals("Ошибки: 0", viewModel.previewErrorCountTextProperty().get());
        assertEquals("Предупреждения: 1", viewModel.previewWarningCountTextProperty().get());
        assertEquals(
                OrderImportViewModel.MSG_PREVIEW_WARNINGS,
                viewModel.previewStatusTextProperty().get());
        assertTrue(viewModel.problemsTableVisibleProperty().get());
        assertTrue(viewModel.errorMessageProperty().get().isBlank());
    }

    @Test
    void multiOrderPreviewShowsOrderCount() {
        FakeImportService imports = new FakeImportService();
        imports.preview = successPreview("ORD-A, ORD-B", 2, 4, "6", 8);
        OrderImportViewModel viewModel =
                new OrderImportViewModel(imports, new FakeStxtParser(), createAuth());

        viewModel.selectFile(Path.of("multi.stxt"));

        assertEquals(2, viewModel.previewOrderCountForTest());
        assertEquals("Заказов: 2", viewModel.previewOrdersTextProperty().get());
        assertEquals("Номера: ORD-A, ORD-B", viewModel.previewOrderNumbersTextProperty().get());
        assertEquals("Импортировать заказы", viewModel.importButtonTextProperty().get());
        assertEquals("Импортировать 2 заказа?", viewModel.confirmationTitle());
        assertEquals(
                "После импорта заказы будут переданы в работу и станут недоступны для редактирования.",
                viewModel.confirmationBody());
        assertEquals("Импортировать заказы", viewModel.confirmationConfirmLabel());
        assertTrue(viewModel.canImportProperty().get());
    }

    @Test
    void successStateShowsFormattedConfirmResult() {
        FakeImportService imports = new FakeImportService();
        imports.preview = successPreview("26062891", 5, "5", 20);
        imports.confirmResult = OrderImportConfirmResult.of(
                OrderId.generate(),
                "26062891",
                5,
                20);
        OrderImportViewModel viewModel =
                new OrderImportViewModel(imports, new FakeStxtParser(), createAuth());
        viewModel.selectFile(Path.of("ok.stxt"));

        viewModel.confirmImport();

        assertEquals(1, imports.confirmCalls.get());
        assertFalse(viewModel.canImportProperty().get());
        assertTrue(viewModel.successVisibleProperty().get());
        assertFalse(viewModel.workingVisibleProperty().get());
        assertEquals("Импорт завершён", viewModel.successTitleProperty().get());
        assertEquals(
                "Заказ №26062891 создан и передан в работу.\nПозиций создано: 5\nСтрок спецификации: 20",
                viewModel.successMessageProperty().get());
        assertTrue(viewModel.canOpenImportedOrderProperty().get());
    }

    @Test
    void singleSuccessEnablesOpenImportedOrder() {
        FakeImportService imports = new FakeImportService();
        imports.preview = successPreview("ORD-1", 1, "1", 1);
        OrderId created = OrderId.generate();
        imports.confirmResult = OrderImportConfirmResult.of(created, "ORD-1", 1, 1);
        AtomicReference<OrderId> opened = new AtomicReference<>();
        OrderImportViewModel viewModel =
                new OrderImportViewModel(imports, new FakeStxtParser(), createAuth());
        viewModel.setOnOpenImportedOrder(opened::set);
        viewModel.selectFile(Path.of("ok.stxt"));

        viewModel.confirmImport();
        viewModel.openImportedOrder();

        assertTrue(viewModel.canOpenImportedOrderProperty().get());
        assertEquals(1, viewModel.lastConfirmResultForTest().createdOrderCount());
        assertEquals(created, opened.get());
    }

    @Test
    void multiSuccessDoesNotEnableOpenImportedOrder() {
        FakeImportService imports = new FakeImportService();
        imports.preview = successPreview("ORD-A, ORD-B", 2, 2, "2", 2);
        imports.confirmResult = OrderImportConfirmResult.of(
                List.of(
                        OrderImportConfirmResult.ImportedOrder.of(OrderId.generate(), "ORD-A"),
                        OrderImportConfirmResult.ImportedOrder.of(OrderId.generate(), "ORD-B")),
                2,
                2);
        AtomicReference<OrderId> opened = new AtomicReference<>();
        OrderImportViewModel viewModel =
                new OrderImportViewModel(imports, new FakeStxtParser(), createAuth());
        viewModel.setOnOpenImportedOrder(opened::set);
        viewModel.selectFile(Path.of("multi.stxt"));

        viewModel.confirmImport();
        viewModel.openImportedOrder();

        assertTrue(viewModel.successVisibleProperty().get());
        assertFalse(viewModel.canOpenImportedOrderProperty().get());
        assertNull(opened.get());
        assertTrue(viewModel.successMessageProperty().get().contains("Импортировано заказов: 2"));
    }

    @Test
    void confirmConflictShowsExactUserMessage() {
        FakeImportService imports = new FakeImportService();
        imports.preview = successPreview("ORD-CF", 1, "1", 1);
        imports.confirmException = new OrderImportConflictException();
        OrderImportViewModel viewModel =
                new OrderImportViewModel(imports, new FakeStxtParser(), createAuth());
        viewModel.selectFile(Path.of("cf.stxt"));

        viewModel.confirmImport();

        assertEquals(
                OrderImportConflictException.USER_MESSAGE, viewModel.errorMessageProperty().get());
        assertFalse(viewModel.canImportProperty().get());
        assertFalse(viewModel.successVisibleProperty().get());
    }

    @Test
    void confirmValidationShowsPreviewUserMessage() {
        FakeImportService imports = new FakeImportService();
        imports.preview = successPreview("ORD-VAL", 1, "1", 1);
        imports.confirmException = new OrderImportValidationException(List.of(
                OrderImportProblem.error(
                        "CODE",
                        "file",
                        null,
                        null,
                        "orderNumber",
                        null,
                        "Файл не содержит обязательную колонку Артикул")));
        OrderImportViewModel viewModel =
                new OrderImportViewModel(imports, new FakeStxtParser(), createAuth());
        viewModel.selectFile(Path.of("val.stxt"));

        viewModel.confirmImport();

        assertEquals(
                "Файл не содержит обязательную колонку Артикул",
                viewModel.errorMessageProperty().get());
        assertEquals(1, viewModel.errors().size());
        assertFalse(viewModel.errorMessageProperty().get().equals("Ошибка импорта"));
    }

    @Test
    void cancelClearsStateWithoutConfirm() {
        FakeImportService imports = new FakeImportService();
        imports.preview = successPreview("ORD-C", 1, "1", 1);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        OrderImportViewModel viewModel =
                new OrderImportViewModel(imports, new FakeStxtParser(), createAuth());
        viewModel.setOnCancel(() -> cancelled.set(true));
        viewModel.selectFile(Path.of("cancel.stxt"));
        assertTrue(viewModel.canImportProperty().get());

        viewModel.cancel();

        assertEquals(0, imports.confirmCalls.get());
        assertTrue(cancelled.get());
        assertEquals("", viewModel.fileNameProperty().get());
        assertNull(viewModel.selectedFileForTest());
        assertFalse(viewModel.fileSelectedProperty().get());
        assertFalse(viewModel.canImportProperty().get());
        assertEquals("Ошибки: 0", viewModel.previewErrorCountTextProperty().get());
        assertEquals("", viewModel.statusMessageProperty().get());
        assertFalse(viewModel.successVisibleProperty().get());
    }

    @Test
    void importAnotherResetsToFileSelection() {
        FakeImportService imports = new FakeImportService();
        imports.preview = successPreview("ORD-1", 1, "1", 1);
        imports.confirmResult = OrderImportConfirmResult.of(OrderId.generate(), "ORD-1", 1, 1);
        OrderImportViewModel viewModel =
                new OrderImportViewModel(imports, new FakeStxtParser(), createAuth());
        viewModel.selectFile(Path.of("ok.stxt"));
        viewModel.confirmImport();
        assertTrue(viewModel.successVisibleProperty().get());

        viewModel.importAnother();

        assertFalse(viewModel.successVisibleProperty().get());
        assertTrue(viewModel.workingVisibleProperty().get());
        assertEquals("", viewModel.fileNameProperty().get());
        assertFalse(viewModel.fileSelectedProperty().get());
        assertNull(viewModel.selectedFileForTest());
        assertFalse(viewModel.canImportProperty().get());
        assertFalse(viewModel.canOpenImportedOrderProperty().get());
        assertEquals("", viewModel.successMessageProperty().get());
        assertEquals("", viewModel.previewOrdersTextProperty().get());
        assertTrue(viewModel.canSelectFileProperty().get());
    }

    @Test
    void missingCreatePermissionDisablesActions() {
        FakeStxtParser stxt = new FakeStxtParser();
        FakeImportService imports = new FakeImportService();
        imports.preview = successPreview("ORD-1", 1, "1", 1);
        OrderImportViewModel viewModel =
                new OrderImportViewModel(imports, stxt, new FakeAuthorization());

        assertFalse(viewModel.canSelectFileProperty().get());
        assertFalse(viewModel.canImportProperty().get());
        assertEquals(OrderUiErrorMapper.ACCESS_DENIED, viewModel.errorMessageProperty().get());

        viewModel.selectFile(Path.of("denied.stxt"));

        assertEquals(0, stxt.parseCalls.get());
        assertEquals(0, imports.previewCalls.get());
        assertFalse(viewModel.fileSelectedProperty().get());
        assertFalse(viewModel.canImportProperty().get());
    }

    @Test
    void conflictOnPreviewShowsRussianMessageWithoutImport() {
        FakeImportService imports = new FakeImportService();
        imports.previewException = new OrderImportConflictException();
        OrderImportViewModel viewModel =
                new OrderImportViewModel(imports, new FakeStxtParser(), createAuth());

        viewModel.selectFile(Path.of("dup.stxt"));

        assertFalse(viewModel.canImportProperty().get());
        assertEquals(OrderImportConflictException.USER_MESSAGE, viewModel.errorMessageProperty().get());
        assertEquals(0, imports.confirmCalls.get());
    }

    private static OrderImportPreview successPreview(
            String orderNumber, int positions, String quantity, int lines) {
        return successPreview(orderNumber, 1, positions, quantity, lines);
    }

    private static OrderImportPreview successPreview(
            String orderNumber, int orderCount, int positions, String quantity, int lines) {
        return OrderImportPreview.of(
                "file.stxt",
                orderNumber,
                orderCount,
                positions,
                new BigDecimal(quantity),
                lines,
                List.of(),
                List.of(),
                new FakePlan(orderNumber));
    }

    private static FakeAuthorization createAuth() {
        return new FakeAuthorization(PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION));
    }

    private static final class FakeStxtParser implements StxtOrderFileParser {
        private final AtomicInteger parseCalls = new AtomicInteger();
        private OrderImportFileParseResult result = OrderImportFileParseResult.of(
                List.of(testBatch("ORD-1")),
                List.of(),
                List.of(),
                "UTF-8");

        @Override
        public OrderImportFileParseResult parseFile(Path file) {
            parseCalls.incrementAndGet();
            return result;
        }

        @Override
        public OrderImportFileParseResult parse(byte[] content, String sourceReference) {
            return result;
        }
    }

    private static final class FakeImportService implements OrderImportService {
        private final AtomicInteger previewCalls = new AtomicInteger();
        private final AtomicInteger confirmCalls = new AtomicInteger();
        private final AtomicReference<PreparedOrderImportPlan> lastConfirmedPlan =
                new AtomicReference<>();
        private OrderImportPreview preview;
        private RuntimeException previewException;
        private RuntimeException confirmException;
        private OrderImportConfirmResult confirmResult;

        @Override
        public OrderImportPreview preview(OrderImportBatch batch) {
            previewCalls.incrementAndGet();
            if (previewException != null) {
                throw previewException;
            }
            return Objects.requireNonNull(preview, "preview");
        }

        @Override
        public OrderImportPreview preview(List<OrderImportBatch> batches) {
            previewCalls.incrementAndGet();
            if (previewException != null) {
                throw previewException;
            }
            return Objects.requireNonNull(preview, "preview");
        }

        @Override
        public OrderImportConfirmResult confirm(PreparedOrderImportPlan plan) {
            confirmCalls.incrementAndGet();
            lastConfirmedPlan.set(plan);
            if (confirmException != null) {
                throw confirmException;
            }
            return Objects.requireNonNull(confirmResult, "confirmResult");
        }
    }

    private static final class FakePlan implements PreparedOrderImportPlan {
        private final String orderNumber;

        private FakePlan(String orderNumber) {
            this.orderNumber = orderNumber;
        }

        @Override
        public List<OrderImportBatch> batches() {
            return List.of(batch());
        }

        @Override
        public OrderImportBatch batch() {
            return testBatch(orderNumber);
        }

        @Override
        public String sourceType() {
            return "STXT";
        }

        @Override
        public String sourceReference() {
            return "file.stxt";
        }

        @Override
        public String contentChecksum() {
            return "cs";
        }

        @Override
        public String orderNumber() {
            return orderNumber;
        }
    }

    private static OrderImportBatch testBatch(String orderNumber) {
        return OrderImportBatch.of(
                "STXT",
                "file.stxt",
                "cs",
                orderNumber,
                LocalDate.of(2026, 6, 25),
                null,
                "Test Client",
                List.of());
    }
}
