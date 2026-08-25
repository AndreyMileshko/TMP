/**
 * Public Production API boundary for cross-capability Query contracts and UI-facing Application API.
 *
 * <p>{@link com.tmp.production.api.ProductionQueryApi} is the read-only inter-capability contract.
 * {@link com.tmp.production.api.ProductionApplicationApi} is the UI/internal mutating use-case
 * boundary (Production Spec §18.2) and is not a Public mutating API for other Capabilities.
 */
package com.tmp.production.api;
