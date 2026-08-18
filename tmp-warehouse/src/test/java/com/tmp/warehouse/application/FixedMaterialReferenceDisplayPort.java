package com.tmp.warehouse.application;

import com.tmp.warehouse.api.MaterialReferenceDisplay;
import com.tmp.warehouse.api.MaterialReferenceDisplayPort;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Test double for {@link MaterialReferenceDisplayPort}. */
public final class FixedMaterialReferenceDisplayPort implements MaterialReferenceDisplayPort {

    private final Map<String, MaterialReferenceDisplay> byArticle = new ConcurrentHashMap<>();
    private final MaterialReferenceDisplayPort fallback;

    public FixedMaterialReferenceDisplayPort() {
        this(new CodeOnlyMaterialReferenceDisplayPort());
    }

    public FixedMaterialReferenceDisplayPort(MaterialReferenceDisplayPort fallback) {
        this.fallback = fallback;
    }

    public FixedMaterialReferenceDisplayPort register(
            String article,
            String materialName,
            String color,
            String size,
            String unitOfMeasure) {
        byArticle.put(
                article,
                MaterialReferenceDisplay.of(article, materialName, color, size, unitOfMeasure));
        return this;
    }

    @Override
    public MaterialReferenceDisplay resolve(String materialCode) {
        return byArticle.getOrDefault(materialCode, fallback.resolve(materialCode));
    }
}
