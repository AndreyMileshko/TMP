package com.tmp.ui.shell.screen.warehouse;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Temporary diagnostic instrumentation for Stocks table refresh jitter.
 *
 * <p>Enable with {@code -Dtmp.warehouse.stocks.refresh.trace=true}. Logs one structured line per
 * reload/apply so layout causes can be proven from runtime evidence.
 */
public final class StocksRefreshTrace {

    public static final String SYSTEM_PROPERTY = "tmp.warehouse.stocks.refresh.trace";

    private static final AtomicLong ACTION_SEQUENCE = new AtomicLong();
    private static final AtomicInteger RELOADS_IN_ACTION = new AtomicInteger();
    private static final AtomicLong CURRENT_ACTION_ID = new AtomicLong();

    private StocksRefreshTrace() {}

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(SYSTEM_PROPERTY, "false"));
    }

    /** Starts a logical user-action scope (search / filter / op completion). */
    public static long beginAction(String reason) {
        if (!enabled()) {
            return -1L;
        }
        long actionId = ACTION_SEQUENCE.incrementAndGet();
        CURRENT_ACTION_ID.set(actionId);
        RELOADS_IN_ACTION.set(0);
        log(
                "ACTION_BEGIN",
                "actionId="
                        + actionId
                        + " reason="
                        + reason
                        + " ts="
                        + System.currentTimeMillis());
        return actionId;
    }

    public static void endAction(long actionId) {
        if (!enabled() || actionId < 0L) {
            return;
        }
        log(
                "ACTION_END",
                "actionId="
                        + actionId
                        + " reloadCount="
                        + RELOADS_IN_ACTION.get()
                        + " ts="
                        + System.currentTimeMillis());
        if (CURRENT_ACTION_ID.get() == actionId) {
            CURRENT_ACTION_ID.set(0L);
        }
    }

    public static void reloadRequested(
            String reason,
            int rowsBefore,
            Object itemsIdentity,
            String warehouseFilter,
            String cellFilter) {
        if (!enabled()) {
            return;
        }
        int seq = RELOADS_IN_ACTION.incrementAndGet();
        log(
                "RELOAD",
                "actionId="
                        + CURRENT_ACTION_ID.get()
                        + " reason="
                        + reason
                        + " reloadSequence="
                        + seq
                        + " rowsBefore="
                        + rowsBefore
                        + " itemsIdentity="
                        + identity(itemsIdentity)
                        + " warehouseFilter="
                        + warehouseFilter
                        + " cellFilter="
                        + cellFilter
                        + " ts="
                        + System.currentTimeMillis());
    }

    public static void applyPage(
            String reason,
            int rowsBefore,
            int rowsAfter,
            Object itemsIdentityBefore,
            Object itemsIdentityAfter,
            String selectedKeys,
            String warehouseFilter,
            String cellFilter,
            boolean loadingCleared) {
        if (!enabled()) {
            return;
        }
        log(
                "APPLY",
                "actionId="
                        + CURRENT_ACTION_ID.get()
                        + " reason="
                        + reason
                        + " rowsBefore="
                        + rowsBefore
                        + " rowsAfter="
                        + rowsAfter
                        + " itemsIdentityBefore="
                        + identity(itemsIdentityBefore)
                        + " itemsIdentityAfter="
                        + identity(itemsIdentityAfter)
                        + " selectedKeys="
                        + selectedKeys
                        + " warehouseFilter="
                        + warehouseFilter
                        + " cellFilter="
                        + cellFilter
                        + " loadingCleared="
                        + loadingCleared
                        + " sameItemsInstance="
                        + (itemsIdentityBefore == itemsIdentityAfter)
                        + " ts="
                        + System.currentTimeMillis());
    }

    public static void layoutSnapshot(
            String phase,
            double tableWidth,
            double tableHeight,
            boolean vScrollbarVisible,
            boolean hScrollbarVisible,
            boolean loadingLabelVisible,
            boolean loadingLabelManaged,
            double loadingLabelHeight,
            int topIndex) {
        if (!enabled()) {
            return;
        }
        log(
                "LAYOUT",
                "actionId="
                        + CURRENT_ACTION_ID.get()
                        + " phase="
                        + phase
                        + " tableW="
                        + tableWidth
                        + " tableH="
                        + tableHeight
                        + " vScrollbar="
                        + vScrollbarVisible
                        + " hScrollbar="
                        + hScrollbarVisible
                        + " loadingVisible="
                        + loadingLabelVisible
                        + " loadingManaged="
                        + loadingLabelManaged
                        + " loadingH="
                        + loadingLabelHeight
                        + " topIndex="
                        + topIndex
                        + " ts="
                        + System.currentTimeMillis());
    }

    private static String identity(Object value) {
        return value == null ? "null" : Integer.toHexString(System.identityHashCode(value));
    }

    private static void log(String kind, String details) {
        System.out.println(
                "[StocksRefresh] kind="
                        + kind
                        + " marker="
                        + StocksTableStabilityMarker.MARKER
                        + " "
                        + Objects.requireNonNull(details, "details"));
    }
}
