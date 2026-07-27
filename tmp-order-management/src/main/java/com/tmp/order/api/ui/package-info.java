/**
 * Application / UI-facing orchestration over Document Engine and typed Order Management payloads.
 *
 * <p>This package is <strong>not</strong> an inter-capability mutating API. Callers orchestrate
 * document create / draft save / post only; they must not access aggregates or repositories.
 * Business mutations occur solely inside document processors after {@code postDocument}.
 */
package com.tmp.order.api.ui;
