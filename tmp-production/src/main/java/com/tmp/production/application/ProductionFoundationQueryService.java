package com.tmp.production.application;

import com.tmp.production.application.port.OrderSpecificationQueryPort;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedSpecification;
import com.tmp.production.domain.FrozenSpecificationUnavailableException;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only access to frozen production specifications after Launch.
 *
 * <p>Always resolves specification content via {@link OrderSpecificationQueryPort#resolveById}.
 * Never uses current/active specification APIs.
 */
public final class ProductionFoundationQueryService {

    private final OrderSpecificationQueryPort specificationQuery;

    public ProductionFoundationQueryService(OrderSpecificationQueryPort specificationQuery) {
        this.specificationQuery = Objects.requireNonNull(specificationQuery, "specificationQuery");
    }

    public ProductionFoundation foundationOf(ProductionItemState state) {
        Objects.requireNonNull(state, "state");
        return state.foundation();
    }

    public Optional<ResolvedSpecification> resolveFrozenSpecification(ProductionFoundation foundation) {
        Objects.requireNonNull(foundation, "foundation");
        return specificationQuery.resolveById(foundation.specificationId());
    }

    public Optional<ResolvedSpecification> resolveFrozenSpecification(ProductionItemState state) {
        return resolveFrozenSpecification(foundationOf(state));
    }

    /** Material lines from the frozen specification (for Material Check, Transfer, Release). */
    public List<ResolvedMaterialLine> materialLines(ProductionFoundation foundation) {
        return resolveFrozenSpecification(foundation)
                .map(ResolvedSpecification::materialLines)
                .orElseThrow(
                        () ->
                                new FrozenSpecificationUnavailableException(
                                        foundation.specificationId()));
    }

    public List<ResolvedMaterialLine> materialLines(ProductionItemState state) {
        return materialLines(foundationOf(state));
    }
}
