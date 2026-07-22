package com.tmp.capability.registration;

/**
 * Unchecked failure raised when Capability registration cannot complete atomically.
 * Always preserves the original failure as {@link #getCause()}; any compensation
 * failure encountered while unwinding Capability-Engine-owned state is attached via
 * {@link #addSuppressed(Throwable)}.
 */
public final class CapabilityRegistrationException extends RuntimeException {

    public CapabilityRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
