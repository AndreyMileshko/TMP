package com.tmp.ui.shell.screen.warehouse;

import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintLineView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Splits transfer print lines into pages for A4 landscape multi-page printing. Pure logic — no
 * JavaFX / PrinterJob dependency. Accounts for wrapped material descriptions via row-unit budget.
 *
 * <p>Landscape provides more width but less height than portrait; defaults use a lower row-unit
 * budget and a wider name wrap estimate.
 */
public final class WarehouseTransferPrintPagination {

    /**
     * Conservative row-unit budget for A4 landscape with header/footer and wrapped names. Lower
     * than portrait because printable height shrinks in landscape.
     */
    public static final int DEFAULT_ROW_UNITS_PER_PAGE = 12;

    /** Approximate characters per line in the Наименование column on A4 landscape. */
    public static final int NAME_CHARS_PER_LINE = 44;

    private WarehouseTransferPrintPagination() {}

    public static List<List<TransferDocumentPrintLineView>> paginate(
            TransferDocumentPrintView document) {
        return paginate(document, DEFAULT_ROW_UNITS_PER_PAGE);
    }

    public static List<List<TransferDocumentPrintLineView>> paginate(
            TransferDocumentPrintView document, int rowUnitsPerPage) {
        Objects.requireNonNull(document, "document");
        if (rowUnitsPerPage < 1) {
            throw new IllegalArgumentException("rowUnitsPerPage must be >= 1: " + rowUnitsPerPage);
        }
        List<TransferDocumentPrintLineView> lines = document.lines();
        if (lines.isEmpty()) {
            return List.of(List.of());
        }
        List<List<TransferDocumentPrintLineView>> pages = new ArrayList<>();
        List<TransferDocumentPrintLineView> current = new ArrayList<>();
        int used = 0;
        for (TransferDocumentPrintLineView line : lines) {
            int units = estimateRowUnits(line);
            if (!current.isEmpty() && used + units > rowUnitsPerPage) {
                pages.add(List.copyOf(current));
                current = new ArrayList<>();
                used = 0;
            }
            current.add(line);
            used += units;
        }
        if (!current.isEmpty()) {
            pages.add(List.copyOf(current));
        }
        return List.copyOf(pages);
    }

    public static int estimateRowUnits(TransferDocumentPrintLineView line) {
        Objects.requireNonNull(line, "line");
        int nameLen = line.materialName() == null ? 0 : line.materialName().length();
        int articleLen = line.article() == null ? 0 : line.article().length();
        int maxLen = Math.max(nameLen, articleLen);
        int wrap = Math.max(1, (maxLen + NAME_CHARS_PER_LINE - 1) / NAME_CHARS_PER_LINE);
        return Math.min(4, wrap);
    }
}
