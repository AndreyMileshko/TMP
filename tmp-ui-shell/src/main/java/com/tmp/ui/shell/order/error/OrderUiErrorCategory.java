package com.tmp.ui.shell.order.error;

/** Classified failure category for Order Management UI messages. */
public enum OrderUiErrorCategory {
    ACCESS_DENIED,
    OPTIMISTIC_LOCK,
    VALIDATION,
    NOT_FOUND,
    FORBIDDEN_TRANSITION,
    ALREADY_POSTED,
    UNPOST_NOT_SUPPORTED,
    TECHNICAL_FAILURE
}
