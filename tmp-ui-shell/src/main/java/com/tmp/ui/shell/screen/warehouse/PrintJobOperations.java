package com.tmp.ui.shell.screen.warehouse;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.Locale;
import java.util.Objects;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Bridge over {@link PrinterJob} so Warehouse printing can enforce create → A4 landscape → native
 * dialog → confirm → printPage/export → endJob in tests without a physical printer.
 *
 * <p>Microsoft Print to PDF (and similar virtual PDF drivers) frequently ignore {@link
 * PageOrientation#LANDSCAPE} and emit a portrait MediaBox ({@code 595×842}). For those printers the
 * production path exports a real A4 landscape PDF after the native dialog instead of spooling
 * through the driver.
 */
interface PrintJobOperations {

    /** Approved Warehouse print page orientation (A4 landscape). */
    PageOrientation PAGE_ORIENTATION = PageOrientation.LANDSCAPE;

    /** A4 landscape MediaBox width in points (long edge). */
    double A4_LANDSCAPE_WIDTH_PT = Paper.A4.getHeight();

    /** A4 landscape MediaBox height in points (short edge). */
    double A4_LANDSCAPE_HEIGHT_PT = Paper.A4.getWidth();

    boolean hasAvailablePrinters();

    /** True when a native print UI cannot be shown (JavaFX would silently auto-confirm). */
    boolean isUiUnavailable();

    PrinterJob createJob();

    /**
     * Forces A4 {@link PageOrientation#LANDSCAPE} on the job before (and after) the native dialog
     * so physical printers open/print in landscape.
     */
    void configureA4Landscape(PrinterJob job);

    boolean showNativePrintDialog(PrinterJob job, Window owner);

    /**
     * Creates and applies a verified A4 landscape {@link PageLayout} for the job's selected
     * printer. Throws {@link IllegalStateException} when the printer cannot provide landscape A4
     * (printable width must exceed printable height).
     */
    PageLayout requireA4LandscapeLayout(PrinterJob job);

    /**
     * Production uses {@link #isLandscapeLayout(PageLayout)}; tests may override when no OS printer
     * is installed.
     */
    default boolean isVerifiedLandscape(PageLayout pageLayout) {
        return isLandscapeLayout(pageLayout);
    }

    /** Always returns an A4 landscape layout for the job's selected printer. */
    PageLayout createPageLayout(PrinterJob job);

    /**
     * Virtual PDF writers that ignore JavaFX/Windows landscape orientation for MediaBox. Detected
     * by printer name only.
     */
    boolean isPdfOutputPrinter(Printer printer);

    /**
     * Asks where to save a landscape PDF when {@link #isPdfOutputPrinter(Printer)} is true. Returns
     * {@code null} when the user cancels.
     */
    File choosePdfOutputFile(Window owner, String suggestedFileName);

    boolean printPage(PrinterJob job, PageLayout pageLayout, Node pageNode);

    void end(PrinterJob job);

    void cancel(PrinterJob job);

    static PageLayout a4LandscapeLayout(Printer printer) {
        Objects.requireNonNull(printer, "printer");
        return printer.createPageLayout(Paper.A4, PAGE_ORIENTATION, Printer.MarginType.DEFAULT);
    }

    static boolean isLandscapeLayout(PageLayout layout) {
        if (layout == null) {
            return false;
        }
        // Printable width must exceed height — the runtime proof of landscape, independent of
        // whether the driver later emits a portrait PDF MediaBox.
        return layout.getPageOrientation() == PAGE_ORIENTATION
                && layout.getPrintableWidth() > layout.getPrintableHeight();
    }

    static boolean looksLikePdfPrinterName(String printerName) {
        if (printerName == null || printerName.isBlank()) {
            return false;
        }
        String n = printerName.toLowerCase(Locale.ROOT);
        return n.contains("print to pdf")
                || n.contains("microsoft print to pdf")
                || n.contains("pdf24")
                || n.contains("adobe pdf")
                || n.contains("foxit")
                || n.contains("cutepdf")
                || n.contains("bullzip")
                || n.contains("nova pdf")
                || n.contains("do pdf")
                || (n.contains("pdf") && (n.contains("writer") || n.contains("creator") || n.contains("virtual")));
    }

    static PrintJobOperations system() {
        return new PrintJobOperations() {
            @Override
            public boolean hasAvailablePrinters() {
                return Printer.getDefaultPrinter() != null || !Printer.getAllPrinters().isEmpty();
            }

            @Override
            public boolean isUiUnavailable() {
                return GraphicsEnvironment.isHeadless();
            }

            @Override
            public PrinterJob createJob() {
                return PrinterJob.createPrinterJob();
            }

            @Override
            public void configureA4Landscape(PrinterJob job) {
                if (job == null) {
                    return;
                }
                Printer printer = job.getPrinter();
                if (printer == null) {
                    return;
                }
                job.getJobSettings().setPageLayout(a4LandscapeLayout(printer));
            }

            @Override
            public boolean showNativePrintDialog(PrinterJob job, Window owner) {
                if (job == null) {
                    return false;
                }
                return job.showPrintDialog(owner);
            }

            @Override
            public PageLayout requireA4LandscapeLayout(PrinterJob job) {
                Objects.requireNonNull(job, "job");
                Printer printer = job.getPrinter();
                if (printer == null) {
                    throw new IllegalStateException("PrinterJob has no selected printer");
                }
                if (!printer.getPrinterAttributes().getSupportedPageOrientations().contains(PAGE_ORIENTATION)) {
                    throw new IllegalStateException(
                            "Выбранный принтер не поддерживает альбомную ориентацию (LANDSCAPE).");
                }
                if (!printer.getPrinterAttributes().getSupportedPapers().contains(Paper.A4)) {
                    throw new IllegalStateException("Выбранный принтер не поддерживает бумагу A4.");
                }
                PageLayout requested = a4LandscapeLayout(printer);
                if (!isLandscapeLayout(requested)) {
                    throw new IllegalStateException(
                            "Выбранный принтер не может обеспечить A4 LANDSCAPE (printable "
                                    + requested.getPrintableWidth()
                                    + "×"
                                    + requested.getPrintableHeight()
                                    + "). Печать портретом запрещена.");
                }
                job.getJobSettings().setPageLayout(requested);
                PageLayout effective = job.getJobSettings().getPageLayout();
                if (!isLandscapeLayout(effective)) {
                    throw new IllegalStateException(
                            "JobSettings не сохранил A4 LANDSCAPE после выбора принтера. Печать отменена.");
                }
                // Prefer the JobSettings instance so printPage and layout share one object.
                return effective;
            }

            @Override
            public PageLayout createPageLayout(PrinterJob job) {
                return requireA4LandscapeLayout(job);
            }

            @Override
            public boolean isPdfOutputPrinter(Printer printer) {
                return printer != null && looksLikePdfPrinterName(printer.getName());
            }

            @Override
            public File choosePdfOutputFile(Window owner, String suggestedFileName) {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Сохранить PDF (A4 альбомный)");
                chooser.getExtensionFilters()
                        .add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
                String name =
                        suggestedFileName == null || suggestedFileName.isBlank()
                                ? "warehouse-print.pdf"
                                : suggestedFileName;
                if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    name = name + ".pdf";
                }
                chooser.setInitialFileName(name);
                return chooser.showSaveDialog(owner);
            }

            @Override
            public boolean printPage(PrinterJob job, PageLayout pageLayout, Node pageNode) {
                return job.printPage(pageLayout, pageNode);
            }

            @Override
            public void end(PrinterJob job) {
                if (job != null) {
                    job.endJob();
                }
            }

            @Override
            public void cancel(PrinterJob job) {
                if (job != null) {
                    job.cancelJob();
                }
            }
        };
    }
}
