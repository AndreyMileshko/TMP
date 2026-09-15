package com.tmp.ui.shell.screen.warehouse;

import com.tmp.ui.shell.order.DecimalQuantityParser;
import com.tmp.ui.shell.order.DecimalUiFormat;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.StockRow;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure helpers for the warehouse move mass-operation dialog (totals, display blanks, quantity
 * validation).
 */
public final class WarehouseMoveDialogSupport {

    private WarehouseMoveDialogSupport() {}

    /**
     * Sums quantities per unit of measure (never across different UoMs). Examples: {@code "12 м.; 3
     * шт."}, {@code "12 м."}.
     */
    public static String formatQuantityTotalsByUnit(List<StockRow> rows) {
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) {
            return "";
        }
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (StockRow row : rows) {
            String unit = displayOrDash(row.unitOfMeasure());
            BigDecimal qty = row.availableQuantity();
            totals.merge(unit, qty, BigDecimal::add);
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append("; ");
            }
            builder.append(DecimalUiFormat.formatRu(entry.getValue()));
            builder.append(' ');
            builder.append(entry.getKey());
            if (!entry.getKey().endsWith(".")) {
                builder.append('.');
            }
        }
        return builder.toString();
    }

    /** Blank or null display fields become an em dash. */
    public static String displayOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }

    /**
     * Parses move quantity: accepts {@code 0,5} / {@code 0.5}; must be {@code > 0} and {@code <=
     * available}.
     */
    public static BigDecimal parseMoveQuantity(String raw, BigDecimal available) {
        Objects.requireNonNull(available, "available");
        BigDecimal qty = DecimalQuantityParser.parsePositive(raw, "количество");
        if (qty.compareTo(available) > 0) {
            throw new IllegalArgumentException(
                    "Количество не может превышать доступный остаток ("
                            + DecimalUiFormat.formatRu(available)
                            + ")");
        }
        return qty;
    }

    /** Same-warehouse destination cell must differ from the source cell of a row. */
    public static void validateNotSelfMove(UUID sourceStorageCellId, UUID destinationStorageCellId) {
        Objects.requireNonNull(sourceStorageCellId, "sourceStorageCellId");
        Objects.requireNonNull(destinationStorageCellId, "destinationStorageCellId");
        if (destinationStorageCellId.equals(sourceStorageCellId)) {
            throw new IllegalArgumentException(
                    "Ячейка назначения должна отличаться от ячейки источника.");
        }
    }
}
