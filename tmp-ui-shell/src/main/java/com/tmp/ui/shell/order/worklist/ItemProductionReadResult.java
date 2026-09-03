package com.tmp.ui.shell.order.worklist;

import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import java.util.Objects;

/**
 * Discriminated result of reading Production item state for operational UI status.
 *
 * <p>{@link SuccessNotAccepted} is a successful Public Query that found no Production row (item not
 * yet accepted). {@link Unavailable} means the query could not be completed (access denied or
 * technical failure). Never collapse those two into one {@code Optional.empty()}.
 */
public sealed interface ItemProductionReadResult
        permits ItemProductionReadResult.SuccessWithState,
                ItemProductionReadResult.SuccessNotAccepted,
                ItemProductionReadResult.Unavailable {

    record SuccessWithState(ItemProductionStateView state) implements ItemProductionReadResult {
        public SuccessWithState {
            Objects.requireNonNull(state, "state");
        }
    }

    record SuccessNotAccepted() implements ItemProductionReadResult {}

    record Unavailable() implements ItemProductionReadResult {}

    static ItemProductionReadResult successWithState(ItemProductionStateView state) {
        return new SuccessWithState(state);
    }

    static ItemProductionReadResult successNotAccepted() {
        return new SuccessNotAccepted();
    }

    static ItemProductionReadResult unavailable() {
        return new Unavailable();
    }
}
