package com.tmp.reference.architecture;

import com.tmp.order.api.OrderQueryService;

/**
 * Test-only architecture violator for COL-010. Intentionally depends on Order Management from a
 * Reference Data package. Never used in production.
 */
public final class ReferenceUsesOrderInternalsViolator {

    @SuppressWarnings("unused")
    private OrderQueryService orderQueryService;

    private ReferenceUsesOrderInternalsViolator() {}
}
