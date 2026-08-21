package com.tmp.production.domain;

/** Source of planned material requirement for availability / transfer template. */
public enum MaterialPlanningSource {
    /** Planned quantity from frozen Specification (current Stage 7 path). */
    SPECIFICATION,
    /**
     * Planned quantity from Cutting Plan data (future Stage 8 read contract).
     *
     * <p>Reserved for when Production can read real Cutting Plan quantities. A mere
     * {@code CuttingPlanId} link must not select this source.
     */
    CUTTING_PLAN
}
