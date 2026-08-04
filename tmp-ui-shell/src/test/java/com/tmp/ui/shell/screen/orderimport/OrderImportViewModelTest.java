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
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.math.BigDecimal;
import java.nio.file.Path;
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
        assertEquals("ORD-1", viewModel.previewOrderNumberProperty().get());
        assertEquals("2", viewModel.previewPositionCountProperty().get());
        assertEquals("3", viewModel.previewProductQuantityProperty().get());
        assertEquals("4", viewModel.previewSpecificationLineCountProperty().get());
        assertEquals("Ошибки: 0", viewModel.previewErrorCountTextProperty().get());
        assertEquals("Предупреждения: 0", viewModel.previewWarningCountTextProperty().get());
        assertTrue(viewModel.canImportProperty().get());
        assertEquals(OrderImportViewModel.MSG_PREVIEW_OK, viewModel.statusMessageProperty().get());
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
                null,
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
        assertEquals(1, viewModel.errors().size());
        assertEquals("Ошибки: 1", viewModel.previewErrorCountTextProperty().get());
        assertEquals("Предупреждения: 0", viewModel.previewWarningCountTextProperty().get());
        assertEquals(
                "Файл не содержит обязательную колонку Артикул",
                viewModel.errorMessageProperty().get());
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
        assertEquals("Ошибки: 0", viewModel.previewErrorCountTextProperty().get());
        assertEquals("Предупреждения: 1", viewModel.previewWarningCountTextProperty().get());
        assertEquals(
                OrderImportViewModel.MSG_PREVIEW_WARNINGS, viewModel.statusMessageProperty().get());
        assertTrue(viewModel.errorMessageProperty().get().isBlank());
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
        assertEquals(
                "Заказ 26062891 успешно импортирован.\nСоздано позиций: 5.\nСтрок спецификации: 20.",
                viewModel.successMessageProperty().get());
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
        assertFalse(viewModel.canImportProperty().get());
        assertEquals("Ошибки: 0", viewModel.previewErrorCountTextProperty().get());
        assertEquals(OrderImportViewModel.MSG_CANCELLED, viewModel.statusMessageProperty().get());
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
        return OrderImportPreview.of(
                "file.stxt",
                orderNumber,
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
                OrderImportBatch.of("STXT", "sample.stxt", "checksum", "ORD-1", List.of()),
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
        public OrderImportBatch batch() {
            return OrderImportBatch.of("STXT", "file.stxt", "cs", orderNumber, List.of());
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
}
