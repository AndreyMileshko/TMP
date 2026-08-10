package com.tmp.bootstrap;

import com.tmp.order.api.MaterialReferenceDisplayDto;
import com.tmp.order.api.MaterialReferenceDisplayQuery;
import com.tmp.warehouse.application.CodeOnlyMaterialReferenceDisplayPort;
import com.tmp.warehouse.application.port.MaterialReferenceDisplay;
import com.tmp.warehouse.application.port.MaterialReferenceDisplayPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Bridges Order Management ACTIVE Specification lines to Warehouse material display reads.
 */
@Configuration
public class WarehouseMaterialDisplayConfiguration {

    @Bean
    @Primary
    @ConditionalOnBean(MaterialReferenceDisplayQuery.class)
    MaterialReferenceDisplayPort specificationBackedMaterialReferenceDisplayPort(
            MaterialReferenceDisplayQuery displayQuery,
            CodeOnlyMaterialReferenceDisplayPort fallback) {
        return materialCode ->
                displayQuery.findByMaterialCode(materialCode)
                        .map(WarehouseMaterialDisplayConfiguration::toDisplay)
                        .orElseGet(() -> fallback.resolve(materialCode));
    }

    private static MaterialReferenceDisplay toDisplay(MaterialReferenceDisplayDto dto) {
        return MaterialReferenceDisplay.of(
                dto.article(),
                dto.materialName(),
                dto.color(),
                dto.size(),
                dto.unitOfMeasure());
    }
}
