package com.tmp.production.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable 0..N Cutting Plan links keyed by {@link MaterialReferenceId} (Production Spec §5.2.1).
 *
 * <p>At most one active Cutting Plan per material reference within one Production Item State.
 */
public final class CuttingPlanLinks implements Iterable<ProductionCuttingPlanLink> {

    private static final CuttingPlanLinks EMPTY = new CuttingPlanLinks(List.of());

    private final List<ProductionCuttingPlanLink> links;

    private CuttingPlanLinks(List<ProductionCuttingPlanLink> links) {
        this.links = List.copyOf(links);
    }

    public static CuttingPlanLinks empty() {
        return EMPTY;
    }

    public static CuttingPlanLinks of(List<ProductionCuttingPlanLink> links) {
        Objects.requireNonNull(links, "links");
        if (links.isEmpty()) {
            return EMPTY;
        }
        validateNoDuplicateMaterials(links);
        return new CuttingPlanLinks(links);
    }

    public static CuttingPlanLinks of(ProductionCuttingPlanLink... links) {
        Objects.requireNonNull(links, "links");
        return of(List.of(links));
    }

    public List<ProductionCuttingPlanLink> asList() {
        return links;
    }

    public boolean isEmpty() {
        return links.isEmpty();
    }

    public int size() {
        return links.size();
    }

    public Optional<CuttingPlanId> findCuttingPlanId(MaterialReferenceId materialReferenceId) {
        Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        return links.stream()
                .filter(link -> link.materialReferenceId().equals(materialReferenceId))
                .map(ProductionCuttingPlanLink::cuttingPlanId)
                .findFirst();
    }

    @Override
    public Iterator<ProductionCuttingPlanLink> iterator() {
        return links.iterator();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CuttingPlanLinks that)) {
            return false;
        }
        return links.equals(that.links);
    }

    @Override
    public int hashCode() {
        return links.hashCode();
    }

    @Override
    public String toString() {
        return "CuttingPlanLinks" + links;
    }

    private static void validateNoDuplicateMaterials(List<ProductionCuttingPlanLink> links) {
        Set<MaterialReferenceId> seen = new HashSet<>();
        List<MaterialReferenceId> duplicates = new ArrayList<>();
        for (ProductionCuttingPlanLink link : links) {
            Objects.requireNonNull(link, "link");
            if (!seen.add(link.materialReferenceId())) {
                duplicates.add(link.materialReferenceId());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException(
                    "At most one Cutting Plan per material reference; duplicate: "
                            + duplicates.getFirst());
        }
    }
}
