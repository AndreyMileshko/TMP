package com.tmp.order.application.query;

import com.tmp.order.api.MaterialReferenceDisplayDto;
import java.util.Optional;

/**
 * Read-only port for resolving MaterialReference display fields from ACTIVE Specification lines.
 */
public interface MaterialReferenceDisplayReadPort {

    Optional<MaterialReferenceDisplayDto> findByMaterialCode(String materialCode);
}
