package com.tmp.production.domain;

/**
 * Minimal lifecycle for a Production-owned Material Transfer Template.
 *
 * <p>Warehouse transfer lifecycle remains Warehouse-owned ({@code DRAFT}/{@code SENT}/{@code
 * RECEIVED}).
 */
public enum MaterialTransferTemplateStatus {
    DRAFT,
    CONFIRMED
}
