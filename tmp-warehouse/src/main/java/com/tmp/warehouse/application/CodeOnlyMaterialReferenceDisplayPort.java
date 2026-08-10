package com.tmp.warehouse.application;

import com.tmp.warehouse.application.port.MaterialReferenceDisplay;
import com.tmp.warehouse.application.port.MaterialReferenceDisplayPort;
import com.tmp.warehouse.domain.MaterialReference;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback resolver when extended material display fields are unavailable from the current source.
 */
public final class CodeOnlyMaterialReferenceDisplayPort implements MaterialReferenceDisplayPort {

    private static final Logger LOG =
            LoggerFactory.getLogger(CodeOnlyMaterialReferenceDisplayPort.class);

    static final String UNAVAILABLE_MESSAGE =
            "Material display fields unavailable from current source";

    private final Set<String> warnedCodes = ConcurrentHashMap.newKeySet();

    @Override
    public MaterialReferenceDisplay resolve(String materialCode) {
        String article = MaterialReference.of(materialCode).materialCode();
        if (warnedCodes.add(article)) {
            LOG.warn("{}: materialCode={}", UNAVAILABLE_MESSAGE, article);
        }
        return MaterialReferenceDisplay.ofArticleOnly(article);
    }

    /** Clears one-shot warning cache (tests only). */
    void resetWarnings() {
        warnedCodes.clear();
    }

    /** Returns whether a warning was logged for the given article (tests only). */
    boolean warnedFor(String article) {
        return warnedCodes.contains(Objects.requireNonNull(article, "article"));
    }
}
