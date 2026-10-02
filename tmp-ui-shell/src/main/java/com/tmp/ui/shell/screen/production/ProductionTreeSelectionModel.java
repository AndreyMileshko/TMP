package com.tmp.ui.shell.screen.production;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

/**
 * Business checkbox selection for the Production tree. Independent of JavaFX row focus selection.
 * Source of truth is {@link ProductionOrderItemRef} identities.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "JavaFX property accessors intentionally expose mutable observables")
public final class ProductionTreeSelectionModel {

    public enum OrderCheckState {
        UNCHECKED,
        CHECKED,
        INDETERMINATE
    }

    private final Set<ProductionOrderItemRef> selected = new LinkedHashSet<>();
    private final IntegerProperty selectedCount = new SimpleIntegerProperty(0);
    private final IntegerProperty revision = new SimpleIntegerProperty(0);

    public void selectItem(ProductionOrderItemRef ref) {
        Objects.requireNonNull(ref, "ref");
        if (selected.add(ref)) {
            bump();
        }
    }

    public void deselectItem(ProductionOrderItemRef ref) {
        Objects.requireNonNull(ref, "ref");
        if (selected.remove(ref)) {
            bump();
        }
    }

    public void setItemSelected(ProductionOrderItemRef ref, boolean selected) {
        if (selected) {
            selectItem(ref);
        } else {
            deselectItem(ref);
        }
    }

    public boolean isItemSelected(ProductionOrderItemRef ref) {
        Objects.requireNonNull(ref, "ref");
        return selected.contains(ref);
    }

    public void selectAll(Collection<ProductionOrderItemRef> refs) {
        Objects.requireNonNull(refs, "refs");
        boolean changed = false;
        for (ProductionOrderItemRef ref : refs) {
            Objects.requireNonNull(ref, "ref");
            changed |= selected.add(ref);
        }
        if (changed) {
            bump();
        }
    }

    public void deselectAll(Collection<ProductionOrderItemRef> refs) {
        Objects.requireNonNull(refs, "refs");
        boolean changed = false;
        for (ProductionOrderItemRef ref : refs) {
            Objects.requireNonNull(ref, "ref");
            changed |= selected.remove(ref);
        }
        if (changed) {
            bump();
        }
    }

    public OrderCheckState orderCheckState(UUID orderId, Collection<ProductionOrderItemRef> childRefs) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(childRefs, "childRefs");
        if (childRefs.isEmpty()) {
            return OrderCheckState.UNCHECKED;
        }
        int selectedChildren = 0;
        for (ProductionOrderItemRef ref : childRefs) {
            if (!ref.sourceOrderId().equals(orderId)) {
                throw new IllegalArgumentException("Child ref order mismatch");
            }
            if (selected.contains(ref)) {
                selectedChildren++;
            }
        }
        if (selectedChildren == 0) {
            return OrderCheckState.UNCHECKED;
        }
        if (selectedChildren == childRefs.size()) {
            return OrderCheckState.CHECKED;
        }
        return OrderCheckState.INDETERMINATE;
    }

    /**
     * Returns selected refs in deterministic order (orderId, then itemId). Independent of click
     * order.
     */
    public List<ProductionOrderItemRef> selectedOrderItemRefs() {
        List<ProductionOrderItemRef> copy = new ArrayList<>(selected);
        Collections.sort(copy);
        return List.copyOf(copy);
    }

    public int size() {
        return selected.size();
    }

    public ReadOnlyIntegerProperty selectedCountProperty() {
        return selectedCount;
    }

    /** Bumps when selection changes so checkbox cells can refresh. */
    public ReadOnlyIntegerProperty revisionProperty() {
        return revision;
    }

    /** Drops selections whose identities are no longer present in authoritative data. */
    public void retainOnly(Collection<ProductionOrderItemRef> authoritativeRefs) {
        Objects.requireNonNull(authoritativeRefs, "authoritativeRefs");
        Set<ProductionOrderItemRef> allowed = Set.copyOf(authoritativeRefs);
        boolean changed = selected.retainAll(allowed);
        if (changed) {
            bump();
        }
    }

    public void clear() {
        if (selected.isEmpty()) {
            return;
        }
        selected.clear();
        bump();
    }

    public Set<UUID> selectedOrderIds() {
        return selected.stream()
                .map(ProductionOrderItemRef::sourceOrderId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void bump() {
        selectedCount.set(selected.size());
        revision.set(revision.get() + 1);
    }
}
