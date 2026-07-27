package com.tmp.order.api.ui;

import com.tmp.order.api.OrderItemId;
import java.util.Optional;

/**
 * UI-facing read use case for the item / revision editor.
 *
 * <p>May return Draft Revision data for desktop UI only. Does not alter the Public Query API.
 */
public interface OrderItemEditorQueryService {

    Optional<OrderItemEditorSnapshot> getEditorSnapshot(OrderItemId orderItemId);
}
