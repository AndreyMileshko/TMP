package com.tmp.ui.shell.screen.warehouse;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Temporary diagnostic instrumentation for Tasks table refresh jitter.
 *
 * <p>Enable with {@code -Dtmp.warehouse.tasks.refresh.trace=true}.
 */
public final class TasksRefreshTrace {

    public static final String SYSTEM_PROPERTY = "tmp.warehouse.tasks.refresh.trace";

    private static final AtomicLong ACTION_SEQUENCE = new AtomicLong();
    private static final AtomicInteger RELOADS_IN_ACTION = new AtomicInteger();
    private static final AtomicLong CURRENT_ACTION_ID = new AtomicLong();

    private TasksRefreshTrace() {}

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
            String reason, int rowsBefore, Object itemsIdentity, String warehouseFilter) {
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
                        + " sequence="
                        + seq
                        + " rowsBefore="
                        + rowsBefore
                        + " itemsIdentity="
                        + identity(itemsIdentity)
                        + " warehouseFilter="
                        + warehouseFilter
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
            boolean loadingLabelVisible,
            boolean loadingLabelManaged) {
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
                        + " loadingVisible="
                        + loadingLabelVisible
                        + " loadingManaged="
                        + loadingLabelManaged
                        + " marker="
                        + TasksTableStabilityMarker.MARKER
                        + " ts="
                        + System.currentTimeMillis());
    }

    private static String identity(Object value) {
        return value == null ? "null" : Integer.toHexString(System.identityHashCode(value));
    }

    private static void log(String kind, String details) {
        System.out.println(
                "[TasksRefresh] kind="
                        + kind
                        + " marker="
                        + TasksTableStabilityMarker.MARKER
                        + " "
                        + Objects.requireNonNull(details, "details"));
    }
}
