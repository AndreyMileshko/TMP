package com.tmp.warehouse.testsupport;

import com.tmp.warehouse.domain.MaterialReference;

/** Test helpers for warehouse material references. */
public final class MaterialTestSupport {

    private MaterialTestSupport() {}

    public static MaterialReference material(String article) {
        return MaterialReference.legacyArticle(article);
    }
}
