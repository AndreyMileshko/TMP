package com.tmp.production.domain;

/**
 * Whole-order Cancellation result action for one Production item line (Production Spec §16).
 */
public enum CancellationItemAction {
    /** Unfinished production cancelled; active quantity zeroed, released preserved. */
    CANCELLED_UNFINISHED,
    /** Released item considered and left unchanged. */
    PRESERVED_RELEASED
}
