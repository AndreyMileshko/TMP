package com.tmp.warehouse.domain;

/**
 * Raised when a Warehouse domain invariant or state rule is violated.
 */
public final class InvalidWarehouseStateException extends RuntimeException {

    public InvalidWarehouseStateException(String message) {
        super(message);
    }
}
