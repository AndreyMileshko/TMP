package com.tmp.production.domain;

/**
 * Lifecycle for a Production-owned Material Requirement.
 *
 * <p>{@code DRAFT} → {@code SUBMITTED} (Stage 3.5.10). {@code SUBMITTED} is immutable: lines and
 * destination are frozen and submission metadata is set.
 */
public enum MaterialRequirementStatus {
    DRAFT,
    SUBMITTED
}
