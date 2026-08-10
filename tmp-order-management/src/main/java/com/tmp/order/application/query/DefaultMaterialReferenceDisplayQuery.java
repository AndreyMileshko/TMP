package com.tmp.order.application.query;

import com.tmp.order.api.MaterialReferenceDisplayDto;
import com.tmp.order.api.MaterialReferenceDisplayQuery;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.Optional;

/**
 * Public API adapter for material reference display lookup from ACTIVE Specification lines.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Holds injected read port managed by the Spring container.")
public final class DefaultMaterialReferenceDisplayQuery implements MaterialReferenceDisplayQuery {

    private final MaterialReferenceDisplayReadPort readPort;

    public DefaultMaterialReferenceDisplayQuery(MaterialReferenceDisplayReadPort readPort) {
        this.readPort = Objects.requireNonNull(readPort, "readPort");
    }

    @Override
    public Optional<MaterialReferenceDisplayDto> findByMaterialCode(String materialCode) {
        Objects.requireNonNull(materialCode, "materialCode");
        return readPort.findByMaterialCode(materialCode);
    }
}
