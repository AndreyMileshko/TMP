package com.tmp.production.domain;

/**
 * Item-level production status vocabulary (Production Spec §8).
 *
 * <p>{@link #NOT_STARTED} exists for computed/application semantics only. Persisted
 * {@link ProductionItemState} is created at Launch with a non-null {@link SpecificationId} and
 * never uses {@code NOT_STARTED}.
 */
public enum ProductionStatus {
    NOT_STARTED,
    IN_PRODUCTION,
    PARTIALLY_RELEASED,
    RELEASED,
    CANCELLED
}
