package com.tmp.ui.shell.screen.production;

/**
 * Presentation-only formatting for the Production Workbench material-requests column.
 *
 * <p>Uses the authoritative submitted product coverage metric ({@code submittedProductCoverage}),
 * not currently requestable capacity. Repeated-request lines are omitted until explicitly
 * persisted classification exists — zeros are never fabricated.
 */
public final class MaterialRequestCoverageLabels {

    public static final String EMPTY = "\u2014";
    public static final String COVERED_PREFIX = "\u0423\u0447\u0442\u0435\u043d\u043e: ";

    /**
     * Russian explanation: X is cumulative submitted MR product coverage, not unique physically
     * supplied products.
     */
    public static final String COVERAGE_TOOLTIP =
            "X \u2014 \u0441\u0443\u043c\u043c\u0430\u0440\u043d\u043e\u0435 \u043a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e \u0438\u0437\u0434\u0435\u043b\u0438\u0439, "
                    + "\u0443\u0447\u0442\u0451\u043d\u043d\u044b\u0445 \u0432 \u043f\u043e\u0434\u0430\u043d\u043d\u044b\u0445 \u0437\u0430\u043f\u0440\u043e\u0441\u0430\u0445 \u043c\u0430\u0442\u0435\u0440\u0438\u0430\u043b\u043e\u0432 "
                    + "(\u043d\u0430\u043a\u043e\u043f\u0438\u0442\u0435\u043b\u044c\u043d\u043e). "
                    + "\u042d\u0442\u043e \u0443\u0447\u0451\u0442 \u043f\u043e\u043a\u0440\u044b\u0442\u0438\u044f, \u0430 \u043d\u0435 \u0447\u0438\u0441\u043b\u043e \u0443\u043d\u0438\u043a\u0430\u043b\u044c\u043d\u044b\u0445 "
                    + "\u0444\u0438\u0437\u0438\u0447\u0435\u0441\u043a\u0438 \u043e\u0431\u0435\u0441\u043f\u0435\u0447\u0435\u043d\u043d\u044b\u0445 \u0438\u0437\u0434\u0435\u043b\u0438\u0439. "
                    + "Y \u2014 \u0437\u0430\u043a\u0430\u0437\u0430\u043d\u043d\u043e\u0435 \u043a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e.";

    private MaterialRequestCoverageLabels() {}

    /**
     * Formats cumulative coverage as {@code Учтено: X/Y}, or {@link #EMPTY} when there is no
     * submitted coverage.
     *
     * @param submittedProductCoverage cumulative SUBMITTED product quantity (X)
     * @param orderedQuantity ordered product quantity for the row (Y)
     */
    public static String formatPrimary(long submittedProductCoverage, long orderedQuantity) {
        if (submittedProductCoverage <= 0L) {
            return EMPTY;
        }
        if (orderedQuantity < 0L) {
            throw new IllegalArgumentException("orderedQuantity must be >= 0: " + orderedQuantity);
        }
        return COVERED_PREFIX + submittedProductCoverage + "/" + orderedQuantity;
    }

    /**
     * Parent-order aggregation: sum of per-position submitted coverage over sum of ordered
     * quantities. Returns {@link #EMPTY} when no submitted coverage exists across children.
     */
    public static String formatAggregated(long submittedSum, long orderedSum) {
        return formatPrimary(submittedSum, orderedSum);
    }

    /** Tooltip text for the coverage column (explanation +/- visible cell value). */
    public static String tooltipFor(String cellLabel) {
        if (cellLabel == null || cellLabel.isBlank() || EMPTY.equals(cellLabel)) {
            return COVERAGE_TOOLTIP;
        }
        return cellLabel + "\n\n" + COVERAGE_TOOLTIP;
    }

    /** True when the label carries coverage text (not empty dash). */
    public static boolean hasCoverage(String label) {
        return label != null && label.startsWith(COVERED_PREFIX);
    }
}