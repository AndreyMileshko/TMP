package com.tmp.order.api;

/**
 * Lifecycle status of an order item revision in Stage 5 (Specification §5.3/§9.3 / ADR-031).
 *
 * <p>A revision is editable only while {@link #DRAFT}. Once {@link #ACTIVE} it — and its item
 * specification — becomes immutable (ADR-018). Transition rules are enforced by the Order Item
 * aggregate.
 */
public enum RevisionStatus {

    /** Draft revision; specification and quantity are editable. */
    DRAFT,

    /** Active revision; immutable and the production baseline for the item. */
    ACTIVE
}
