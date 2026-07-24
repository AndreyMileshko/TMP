/**
 * Public API of the TMP Order Management Capability.
 *
 * <p>External modules (other Capabilities and the UI shell) may depend only on types in this
 * package: type-safe identifiers, status value objects, read-only Query DTOs and Domain Events.
 * This package exposes no mutating operation: all state changes flow exclusively through business
 * documents processed inside the Order Management module.
 *
 * <p>Internal {@code domain}, {@code application}, {@code persistence} and {@code capability}
 * packages must never be referenced from outside {@code tmp-order-management}.
 */
package com.tmp.order.api;
