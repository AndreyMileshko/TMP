package com.tmp.order.application.imports;

/**
 * Thread-local gate: while active, {@code ORDER_APPROVE} uses import landing rules (client required;
 * full ADR-030 commercial set not required). Manual UI approve never sets this gate.
 */
public final class ImportApproveGate {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ImportApproveGate() {}

    public static void enter() {
        ACTIVE.set(Boolean.TRUE);
    }

    public static void exit() {
        ACTIVE.remove();
    }

    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }
}
