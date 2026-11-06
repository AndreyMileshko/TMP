package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintLineView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintView;
import com.tmp.warehouse.api.WarehouseApi.TransferPrintAllocationKind;
import com.tmp.warehouse.api.WarehouseApi.TransferPrintDocumentKind;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarehouseTransferPrintDialogPathTest {

    @BeforeAll
    static void startToolkit() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void resolvePrintOwnerPrefersNonModalShellOverModalDialog() throws Exception {
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    Stage shell = new Stage();
                    shell.initModality(Modality.NONE);
                    shell.setScene(new Scene(new Region(), 200, 100));
                    shell.show();

                    Stage modal = new Stage();
                    modal.initOwner(shell);
                    modal.initModality(Modality.WINDOW_MODAL);
                    modal.setScene(new Scene(new Region(), 120, 80));
                    modal.show();

                    assertSame(shell, WarehouseTransferPrintSupport.resolvePrintOwner(modal));
                    shell.close();
                    modal.close();
                });
    }

    @Test
    void cancellationProducesZeroPrintedPages() throws Exception {
        RecordingOps ops = new RecordingOps(false);
        AtomicReference<String> error = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> WarehouseTransferPrintSupport.print(
                        sampleDocument(TransferPrintDocumentKind.TRANSFER_PICKING),
                        null,
                        error::set,
                        ops));
        assertEquals(1, ops.dialogCalls.get());
        assertEquals(0, ops.printPageCalls.get());
        assertEquals(0, ops.endCalls.get());
        assertEquals(1, ops.cancelCalls.get());
        assertEquals(0, ops.pdfChooserCalls.get());
        assertTrue(error.get() == null || error.get().isBlank());
    }

    @Test
    void confirmationAllowsPrintingAfterDialog() throws Exception {
        RecordingOps ops = new RecordingOps(true);
        AtomicReference<String> error = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> WarehouseTransferPrintSupport.print(
                        sampleDocument(TransferPrintDocumentKind.TRANSFER_PICKING),
                        null,
                        error::set,
                        ops));
        assertEquals(1, ops.dialogCalls.get());
        assertTrue(ops.printPageCalls.get() >= 1);
        assertEquals(1, ops.endCalls.get());
        assertEquals(0, ops.cancelCalls.get());
        assertTrue(ops.dialogBeforeFirstPrint.get());
        assertTrue(ops.landscapeConfiguredBeforeDialog.get());
        assertTrue(ops.requireLandscapeAfterDialog.get());
        assertTrue(ops.allPrintPagesUsedSameLayout.get());
        assertTrue(error.get() == null || error.get().isBlank());
    }

    @Test
    void landscapeIsRequiredAfterDialogForBothDocumentKinds() throws Exception {
        for (TransferPrintDocumentKind kind :
                List.of(
                        TransferPrintDocumentKind.TRANSFER_PICKING,
                        TransferPrintDocumentKind.MATERIAL_RETURN)) {
            RecordingOps ops = new RecordingOps(true);
            JavaFxTestSupport.runOnFxThread(
                    () -> WarehouseTransferPrintSupport.print(
                            sampleDocument(kind), null, message -> {}, ops));
            assertTrue(ops.landscapeConfiguredBeforeDialog.get(), "pre-dialog landscape for " + kind);
            assertTrue(ops.requireLandscapeAfterDialog.get(), "post-dialog require for " + kind);
            assertEquals(PageOrientation.LANDSCAPE, PrintJobOperations.PAGE_ORIENTATION);
        }
    }

    @Test
    void switchingPrinterAfterDialogRequeriesLandscapeLayout() throws Exception {
        RecordingOps ops = new RecordingOps(true);
        ops.switchPrinterNameAfterDialog = "Office Laser";
        JavaFxTestSupport.runOnFxThread(
                () -> WarehouseTransferPrintSupport.print(
                        sampleDocument(TransferPrintDocumentKind.TRANSFER_PICKING),
                        null,
                        message -> {},
                        ops));
        assertEquals("Office Laser", ops.printerNameAtRequire.get());
        assertTrue(ops.requireLandscapeAfterDialog.get());
        assertTrue(ops.printPageCalls.get() >= 1);
    }

    @Test
    void pdfPrinterExportsLandscapePdfInsteadOfPrintPage() throws Exception {
        assumeTrue(firstPrinter() != null, "OS printer required for landscape PageLayout");
        File target = Files.createTempFile("tmp-wh-print-", ".pdf").toFile();
        target.deleteOnExit();
        RecordingOps ops = new RecordingOps(true);
        ops.pdfPrinter = true;
        ops.pdfOutputFile = target;
        AtomicReference<String> error = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> WarehouseTransferPrintSupport.print(
                        sampleDocument(TransferPrintDocumentKind.TRANSFER_PICKING),
                        null,
                        error::set,
                        ops));
        assertEquals(1, ops.pdfChooserCalls.get());
        assertEquals(0, ops.printPageCalls.get());
        assertEquals(0, ops.endCalls.get());
        assertTrue(ops.cancelCalls.get() >= 1);
        assertTrue(error.get() == null || error.get().isBlank(), () -> "error=" + error.get());
        byte[] pdf = Files.readAllBytes(target.toPath());
        assertTrue(
                WarehouseTransferLandscapePdfExport.pdfDeclaresA4LandscapeMediaBox(pdf),
                "PDF must declare A4 landscape MediaBox");
        assertTrue(
                PrintJobOperations.A4_LANDSCAPE_WIDTH_PT
                        > PrintJobOperations.A4_LANDSCAPE_HEIGHT_PT);
    }

    @Test
    void returnFormPdfPrinterAlsoExportsLandscape() throws Exception {
        assumeTrue(firstPrinter() != null, "OS printer required for landscape PageLayout");
        File target = Files.createTempFile("tmp-wh-return-", ".pdf").toFile();
        target.deleteOnExit();
        RecordingOps ops = new RecordingOps(true);
        ops.pdfPrinter = true;
        ops.pdfOutputFile = target;
        JavaFxTestSupport.runOnFxThread(
                () -> WarehouseTransferPrintSupport.print(
                        sampleDocument(TransferPrintDocumentKind.MATERIAL_RETURN),
                        null,
                        message -> {},
                        ops));
        assertEquals(1, ops.pdfChooserCalls.get());
        assertEquals(0, ops.printPageCalls.get());
        assertTrue(
                WarehouseTransferLandscapePdfExport.pdfDeclaresA4LandscapeMediaBox(
                        Files.readAllBytes(target.toPath())));
    }

    @Test
    void pdfPrinterExportCancellationSkipsFile() throws Exception {
        RecordingOps ops = new RecordingOps(true);
        ops.pdfPrinter = true;
        ops.pdfOutputFile = null;
        JavaFxTestSupport.runOnFxThread(
                () -> WarehouseTransferPrintSupport.print(
                        sampleDocument(TransferPrintDocumentKind.MATERIAL_RETURN),
                        null,
                        message -> {},
                        ops));
        assertEquals(1, ops.pdfChooserCalls.get());
        assertEquals(0, ops.printPageCalls.get());
        assertEquals(1, ops.cancelCalls.get());
    }

    @Test
    void unsupportedLandscapeSurfacesErrorWithoutPrinting() throws Exception {
        RecordingOps ops = new RecordingOps(true);
        ops.failRequireLandscape = true;
        AtomicReference<String> error = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> WarehouseTransferPrintSupport.print(
                        sampleDocument(TransferPrintDocumentKind.TRANSFER_PICKING),
                        null,
                        error::set,
                        ops));
        assertEquals(0, ops.printPageCalls.get());
        assertEquals(0, ops.pdfChooserCalls.get());
        assertTrue(ops.cancelCalls.get() >= 1);
        assertTrue(error.get() != null && error.get().contains("LANDSCAPE"));
    }

    @Test
    void missingPrinterIsHandledWithoutDialogOrPages() throws Exception {
        RecordingOps ops = new RecordingOps(true);
        ops.availablePrinters = false;
        AtomicReference<String> error = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> WarehouseTransferPrintSupport.print(
                        sampleDocument(TransferPrintDocumentKind.MATERIAL_RETURN),
                        null,
                        error::set,
                        ops));
        assertEquals(0, ops.dialogCalls.get());
        assertEquals(0, ops.printPageCalls.get());
        assertTrue(error.get() != null && error.get().contains("Нет доступных принтеров"));
    }

    @Test
    void headlessUiIsRefusedWithoutSilentPrint() throws Exception {
        RecordingOps ops = new RecordingOps(true);
        ops.uiUnavailable = true;
        AtomicReference<String> error = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> WarehouseTransferPrintSupport.print(
                        sampleDocument(TransferPrintDocumentKind.TRANSFER_PICKING),
                        null,
                        error::set,
                        ops));
        assertEquals(0, ops.dialogCalls.get());
        assertEquals(0, ops.printPageCalls.get());
        assertTrue(error.get() != null && error.get().contains("Диалог печати недоступен"));
    }

    @Test
    void supportedDocumentKindsShareDialogBeforePrintPath() throws Exception {
        for (TransferPrintDocumentKind kind :
                List.of(
                        TransferPrintDocumentKind.TRANSFER_PICKING,
                        TransferPrintDocumentKind.MATERIAL_RETURN)) {
            RecordingOps ops = new RecordingOps(true);
            JavaFxTestSupport.runOnFxThread(
                    () -> WarehouseTransferPrintSupport.print(
                            sampleDocument(kind), null, message -> {}, ops));
            assertEquals(1, ops.dialogCalls.get(), "dialog for " + kind);
            assertTrue(ops.dialogBeforeFirstPrint.get(), "order for " + kind);
            assertTrue(ops.printPageCalls.get() >= 1, "pages for " + kind);
        }
    }

    @Test
    void noDirectPrintBypassWithoutDialogConfirmation() throws Exception {
        RecordingOps ops = new RecordingOps(false);
        JavaFxTestSupport.runOnFxThread(
                () -> WarehouseTransferPrintSupport.print(
                        sampleDocument(TransferPrintDocumentKind.TRANSFER_PICKING),
                        null,
                        message -> {},
                        ops));
        assertFalse(ops.printWithoutDialog.get());
        assertEquals(0, ops.printPageCalls.get());
    }

    @Test
    void pdfPrinterNameDetection() {
        assertTrue(PrintJobOperations.looksLikePdfPrinterName("Microsoft Print to PDF"));
        assertTrue(PrintJobOperations.looksLikePdfPrinterName("PDF24"));
        assertFalse(PrintJobOperations.looksLikePdfPrinterName("MF460 Series"));
        assertFalse(PrintJobOperations.looksLikePdfPrinterName(null));
    }

    @Test
    void sevenColumnHeadersRemainCompleteOnLandscapeWidth() throws Exception {
        assumeTrue(firstPrinter() != null, "OS printer required for landscape PageLayout");
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    PageLayout layout = PrintJobOperations.a4LandscapeLayout(firstPrinter());
                    assertTrue(PrintJobOperations.isLandscapeLayout(layout));
                    assertTrue(layout.getPrintableWidth() > 700, "landscape printable width");
                    var table =
                            WarehouseTransferPrintSupport.buildTable(
                                    List.of(), layout.getPrintableWidth());
                    for (int c = 0; c < WarehouseTransferPrintSupport.COLUMN_HEADERS.size(); c++) {
                        javafx.scene.control.Label header =
                                (javafx.scene.control.Label) table.getChildren().get(c);
                        assertEquals(
                                WarehouseTransferPrintSupport.COLUMN_HEADERS.get(c),
                                header.getText());
                        assertFalse(header.getText().contains("…"));
                        assertFalse(header.getText().contains("..."));
                    }
                    assertEquals("Размер", WarehouseTransferPrintSupport.COLUMN_HEADERS.get(4));
                    assertEquals("Количество", WarehouseTransferPrintSupport.COLUMN_HEADERS.get(5));
                });
    }

    private static Printer firstPrinter() {
        Printer printer = Printer.getDefaultPrinter();
        if (printer != null) {
            return printer;
        }
        var all = Printer.getAllPrinters();
        return all.isEmpty() ? null : all.iterator().next();
    }

    private static TransferDocumentPrintView sampleDocument(TransferPrintDocumentKind kind) {
        String heading =
                kind == TransferPrintDocumentKind.MATERIAL_RETURN
                        ? "ЗАДАНИЕ НА ВОЗВРАТ МАТЕРИАЛОВ"
                        : "ЗАДАНИЕ НА КОМПЛЕКТАЦИЮ МАТЕРИАЛОВ";
        return new TransferDocumentPrintView(
                UUID.randomUUID(),
                "TR-1",
                Instant.parse("2026-09-11T10:00:00Z"),
                "DRAFT",
                "Перемещение",
                kind,
                heading,
                List.of("100"),
                UUID.randomUUID(),
                "SRC",
                "Source",
                UUID.randomUUID(),
                "DST",
                "Destination",
                TransferPrintAllocationKind.PLANNED,
                true,
                null,
                List.of(
                        new TransferDocumentPrintLineView(
                                1,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "A",
                                "Name",
                                "C",
                                "S",
                                "шт",
                                BigDecimal.ONE,
                                null,
                                "A-01",
                                TransferPrintAllocationKind.PLANNED)));
    }

    private static final class RecordingOps implements PrintJobOperations {
        private final boolean confirmDialog;
        private boolean availablePrinters = true;
        private boolean uiUnavailable = false;
        private boolean pdfPrinter = false;
        private boolean failRequireLandscape = false;
        private File pdfOutputFile;
        private String switchPrinterNameAfterDialog;
        private final AtomicInteger dialogCalls = new AtomicInteger();
        private final AtomicInteger printPageCalls = new AtomicInteger();
        private final AtomicInteger endCalls = new AtomicInteger();
        private final AtomicInteger cancelCalls = new AtomicInteger();
        private final AtomicInteger landscapeConfigureCalls = new AtomicInteger();
        private final AtomicInteger pdfChooserCalls = new AtomicInteger();
        private final AtomicReference<Boolean> dialogBeforeFirstPrint = new AtomicReference<>(false);
        private final AtomicReference<Boolean> printWithoutDialog = new AtomicReference<>(false);
        private final AtomicReference<Boolean> landscapeConfiguredBeforeDialog =
                new AtomicReference<>(false);
        private final AtomicReference<Boolean> requireLandscapeAfterDialog =
                new AtomicReference<>(false);
        private final AtomicReference<Boolean> allPrintPagesUsedSameLayout =
                new AtomicReference<>(true);
        private final AtomicReference<String> printerNameAtRequire = new AtomicReference<>();
        private boolean dialogSeen;
        private boolean landscapeSeenBeforeDialog;
        private PageLayout layoutPassedToPrint;
        private String currentPrinterName = "Physical Printer";

        private RecordingOps(boolean confirmDialog) {
            this.confirmDialog = confirmDialog;
        }

        @Override
        public boolean hasAvailablePrinters() {
            return availablePrinters;
        }

        @Override
        public boolean isUiUnavailable() {
            return uiUnavailable;
        }

        @Override
        public PrinterJob createJob() {
            return null;
        }

        @Override
        public void configureA4Landscape(PrinterJob job) {
            landscapeConfigureCalls.incrementAndGet();
            if (!dialogSeen) {
                landscapeSeenBeforeDialog = true;
            }
        }

        @Override
        public boolean showNativePrintDialog(PrinterJob job, Window owner) {
            landscapeConfiguredBeforeDialog.set(landscapeSeenBeforeDialog);
            dialogCalls.incrementAndGet();
            dialogSeen = true;
            if (switchPrinterNameAfterDialog != null) {
                currentPrinterName = switchPrinterNameAfterDialog;
            }
            return confirmDialog;
        }

        @Override
        public PageLayout requireA4LandscapeLayout(PrinterJob job) {
            requireLandscapeAfterDialog.set(dialogSeen);
            printerNameAtRequire.set(currentPrinterName);
            if (failRequireLandscape) {
                throw new IllegalStateException(
                        "Выбранный принтер не может обеспечить A4 LANDSCAPE");
            }
            Printer printer = firstPrinter();
            return printer == null ? null : PrintJobOperations.a4LandscapeLayout(printer);
        }

        @Override
        public boolean isVerifiedLandscape(PageLayout pageLayout) {
            if (failRequireLandscape) {
                return false;
            }
            return pageLayout == null || PrintJobOperations.isLandscapeLayout(pageLayout);
        }

        @Override
        public PageLayout createPageLayout(PrinterJob job) {
            return requireA4LandscapeLayout(job);
        }

        @Override
        public boolean isPdfOutputPrinter(Printer printer) {
            return pdfPrinter;
        }

        @Override
        public File choosePdfOutputFile(Window owner, String suggestedFileName) {
            pdfChooserCalls.incrementAndGet();
            return pdfOutputFile;
        }

        @Override
        public boolean printPage(PrinterJob job, PageLayout pageLayout, Node pageNode) {
            if (!dialogSeen) {
                printWithoutDialog.set(true);
            } else if (printPageCalls.get() == 0) {
                dialogBeforeFirstPrint.set(true);
                layoutPassedToPrint = pageLayout;
            } else if (layoutPassedToPrint != pageLayout) {
                allPrintPagesUsedSameLayout.set(false);
            }
            printPageCalls.incrementAndGet();
            return true;
        }

        @Override
        public void end(PrinterJob job) {
            endCalls.incrementAndGet();
        }

        @Override
        public void cancel(PrinterJob job) {
            cancelCalls.incrementAndGet();
        }
    }
}
