package com.tmp.ui.shell.screen.warehouse;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Temporary diagnostic instrumentation for History table refresh jitter.
 *
 * <p>Enable with {@code -Dtmp.warehouse.history.refresh.trace=true}.
 */
public final class HistoryRefreshTrace {

    public static final String SYSTEM_PROPERTY = "tmp.warehouse.history.refresh.trace";

    private static final AtomicLong ACTION_SEQUENCE = new AtomicLong();
    private static final AtomicInteger RELOADS_IN_ACTION = new AtomicInteger();
    private static final AtomicLong CURRENT_ACTION_ID = new AtomicLong();

    private HistoryRefreshTrace() {}

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(SYSTEM_PROPERTY, "false"));
    }

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
            String operationFilter) {
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
                        + " operationFilter="
                        + operationFilter
                        + " ts="
                        + System.currentTimeMillis());
    }

    public static void applyPage(
            String reason,
            int rowsBefore,
            int rowsAfter,
            Object itemsIdentityBefore,
            Object itemsIdentityAfter,
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
                "[HistoryRefresh] kind="
                        + kind
                        + " marker="
                        + HistoryTableStabilityMarker.MARKER
                        + " "
                        + Objects.requireNonNull(details, "details"));
    }
}
