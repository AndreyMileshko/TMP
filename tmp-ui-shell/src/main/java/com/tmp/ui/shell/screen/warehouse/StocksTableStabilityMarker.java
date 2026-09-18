package com.tmp.ui.shell.screen.warehouse;

/**
 * Runtime identity for packaged builds that include the Stocks table jitter fix.
 *
 * <p>Keep the marker string unique per corrective ship so {@code TMP.exe} / ui-shell jar
 * contents can be verified without relying on local {@code target/classes}.
 */
public final class StocksTableStabilityMarker {

    /** Bytecode-visible marker embedded in the packaged ui-shell jar. */
    public static final String MARKER = "STOCKS_TABLE_JITTER_FIX_2026_09_17";

    private StocksTableStabilityMarker() {}
}
