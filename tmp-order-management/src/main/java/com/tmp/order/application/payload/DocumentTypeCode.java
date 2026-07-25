package com.tmp.order.application.payload;

/**
 * Order Management business document type codes (Specification §13.1).
 *
 * <p>Each typed payload is bound to exactly one code; generic / untyped payload is forbidden
 * (ADR-028).
 */
public enum DocumentTypeCode {

    ORDER_CREATE,
    ORDER_UPDATE,
    ORDER_APPROVE,
    ORDER_CANCEL,
    ORDER_ITEM_CREATE,
    ORDER_ITEM_UPDATE,
    ORDER_ITEM_CANCEL,
    ORDER_ITEM_REVISION_CREATE,
    ORDER_ITEM_REVISION_UPDATE,
    ORDER_ITEM_REVISION_APPROVE
}
