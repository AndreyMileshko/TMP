package com.tmp.production.domain;

import java.util.Objects;

/** Opaque identity of one Production Order Item for coverage / selection keys. */
public record MaterialRequirementSourceItemKey(
        SourceOrderId sourceOrderId, SourceOrderItemId sourceOrderItemId) {

    public MaterialRequirementSourceItemKey {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
    }

    public static MaterialRequirementSourceItemKey of(
            SourceOrderId sourceOrderId, SourceOrderItemId sourceOrderItemId) {
        return new MaterialRequirementSourceItemKey(sourceOrderId, sourceOrderItemId);
    }
}
