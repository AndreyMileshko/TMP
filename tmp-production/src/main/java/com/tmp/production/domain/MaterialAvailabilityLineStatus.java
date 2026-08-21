package com.tmp.production.domain;

/** Production-side availability status for one material requirement line. */
public enum MaterialAvailabilityLineStatus {
    AVAILABLE,
    INSUFFICIENT,
    MATERIAL_UNRESOLVED,
    MATERIAL_AMBIGUOUS
}
