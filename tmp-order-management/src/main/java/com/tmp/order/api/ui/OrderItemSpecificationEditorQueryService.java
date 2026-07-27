package com.tmp.order.api.ui;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import java.util.Optional;

/**
 * UI-facing read use case for the Item Specification editor.
 *
 * <p>Loads Draft or Approved Specification for desktop UI only. Does not alter the Public Query
 * API. Requires {@code order.specification.view}.
 */
public interface OrderItemSpecificationEditorQueryService {

    /**
     * Returns the specification snapshot for the given revision, or empty when the item or
     * revision is absent.
     */
    Optional<OrderItemSpecificationEditorSnapshot> getSpecificationSnapshot(
            OrderItemId orderItemId, RevisionNumber revisionNumber);
}
