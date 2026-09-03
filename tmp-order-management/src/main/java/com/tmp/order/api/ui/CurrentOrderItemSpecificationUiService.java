package com.tmp.order.api.ui;

import com.tmp.order.api.OrderItemId;
import java.util.Optional;

/**
 * UI facade that resolves which revision is the user-visible "current" item specification.
 *
 * <p>Prefers an open draft revision when present; otherwise the active revision. Used by «Открыть
 * спецификацию» so the desktop UI does not choose between active/draft itself.
 */
public interface CurrentOrderItemSpecificationUiService {

    Optional<CurrentOrderItemSpecificationRef> resolveCurrent(OrderItemId orderItemId);
}
