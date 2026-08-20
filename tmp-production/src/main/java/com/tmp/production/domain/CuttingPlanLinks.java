package com.tmp.production.domain;

import java.util.Collections;
import java.util.List;

/**
 * Placeholder for 0..N Cutting Plan references by material (Production Spec §5.2.1, STAGE7-008).
 */
public final class CuttingPlanLinks {

    private static final CuttingPlanLinks EMPTY = new CuttingPlanLinks(List.of());

    private final List<CuttingPlanLink> links;

    private CuttingPlanLinks(List<CuttingPlanLink> links) {
        this.links = List.copyOf(links);
    }

    public static CuttingPlanLinks empty() {
        return EMPTY;
    }

    public List<CuttingPlanLink> links() {
        return Collections.unmodifiableList(links);
    }

    /** Single material-to-cutting-plan reference (STAGE7-008 will populate). */
    public record CuttingPlanLink(String materialReference, String cuttingPlanId) {}
}
