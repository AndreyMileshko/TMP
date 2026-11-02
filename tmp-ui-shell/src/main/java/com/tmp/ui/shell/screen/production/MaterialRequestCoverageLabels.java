package com.tmp.ui.shell.screen.production;

/**
 * Presentation-only formatting for the Production Workbench material-requests column.
 *
 * <p>Uses the authoritative submitted product coverage metric ({@code submittedProductCoverage}),
 * not currently requestable capacity. Repeated-request lines use Phase 1 metrics {@code
 * repeatedSubmittedProductQuantity} / {@code repeatedSubmittedRequestCount} — never fabricated.
 *
 * <p>Parent aggregation sums per-position product quantities and per-source-item repeated request
 * counts from the coverage API. {@code repeatedSubmittedRequestCount} counts SUBMITTED REPEATED
 * source-item rows (not necessarily unique Material Requirement documents).
 */
public final class MaterialRequestCoverageLabels {

    public static final String EMPTY = "—";
    public static final String COVERED_PREFIX = "Учтено: ";
    public static final String REPEATED_PREFIX = "Повторно: ";

    /**
     * Russian explanation: X is cumulative submitted MR product coverage, not unique physically
     * supplied products.
     */
    public static final String COVERAGE_TOOLTIP =
            "X — накопительное учтённое покрытие запросов материалов по изделиям"
                    + " (сумма requested_product_quantity SUBMITTED COUNTED),"
                    + " не уникальные физически поставленные изделия."
                    + " Y — заказанное количество изделий позиции."
                    + " «Повторно» — сумма SUBMITTED REPEATED запросов; M считает source-item"
                    + " вхождения Phase 1 API, не обязательно уникальные документы MR.";

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

    /**
     * Full cell label including optional second line for submitted repeated requests.
     *
     * <p>When {@code repeatedSubmittedRequestCount} and {@code repeatedSubmittedProductQuantity}
     * are both zero, preserves the compact single-line primary format.
     */
    public static String format(
            long submittedProductCoverage,
            long orderedQuantity,
            long repeatedSubmittedProductQuantity,
            long repeatedSubmittedRequestCount) {
        String primary = formatPrimary(submittedProductCoverage, orderedQuantity);
        if (repeatedSubmittedProductQuantity <= 0L && repeatedSubmittedRequestCount <= 0L) {
            return primary;
        }
        return primary
                + "\n"
                + formatRepeated(
                        repeatedSubmittedProductQuantity, repeatedSubmittedRequestCount);
    }

    public static String formatAggregated(
            long submittedSum,
            long orderedSum,
            long repeatedQtySum,
            long repeatedCountSum) {
        return format(submittedSum, orderedSum, repeatedQtySum, repeatedCountSum);
    }

    /**
     * {@code Повторно: N изд. (M запросов)} where N is cumulative REPEATED product quantity and M is
     * the Phase 1 submitted repeated source-item request count.
     */
    public static String formatRepeated(
            long repeatedSubmittedProductQuantity, long repeatedSubmittedRequestCount) {
        if (repeatedSubmittedProductQuantity < 0L) {
            throw new IllegalArgumentException(
                    "repeatedSubmittedProductQuantity must be >= 0: "
                            + repeatedSubmittedProductQuantity);
        }
        if (repeatedSubmittedRequestCount < 0L) {
            throw new IllegalArgumentException(
                    "repeatedSubmittedRequestCount must be >= 0: "
                            + repeatedSubmittedRequestCount);
        }
        return REPEATED_PREFIX
                + repeatedSubmittedProductQuantity
                + " изд. ("
                + repeatedSubmittedRequestCount
                + " "
                + repeatedRequestsWord(repeatedSubmittedRequestCount)
                + ")";
    }

    private static String repeatedRequestsWord(long count) {
        long mod100 = count % 100L;
        long mod10 = count % 10L;
        if (mod100 >= 11L && mod100 <= 14L) {
            return "запросов";
        }
        if (mod10 == 1L) {
            return "запрос";
        }
        if (mod10 >= 2L && mod10 <= 4L) {
            return "запроса";
        }
        return "запросов";
    }

    /** Tooltip text for the coverage column (explanation + / − visible cell value). */
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

    /** True when the label includes a repeated-request statistics line. */
    public static boolean hasRepeated(String label) {
        return label != null && label.contains(REPEATED_PREFIX);
    }
}
