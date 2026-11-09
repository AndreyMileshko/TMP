package com.tmp.order.architecture;

import com.tmp.reference.capability.ReferenceDataCapability;

/**
 * Test-only architecture violator for COL-010. Intentionally depends on Reference Data from an
 * Order Management package. Never used in production.
 */
public final class OrderUsesReferenceInternalsViolator {

    @SuppressWarnings("unused")
    private ReferenceDataCapability capability;

    private OrderUsesReferenceInternalsViolator() {}
}
