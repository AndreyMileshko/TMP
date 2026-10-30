package com.tmp.production.application.port;

import com.tmp.production.domain.SourceOrderId;
import java.util.Optional;

/**
 * Resolves the human customer order number via Order Management public query only.
 *
 * <p>Used when Production stamps Warehouse Consumption origin metadata. Warehouse must not look up
 * order numbers itself.
 */
public interface CustomerOrderNumberQueryPort {

    Optional<String> findOrderNumber(SourceOrderId sourceOrderId);
}
