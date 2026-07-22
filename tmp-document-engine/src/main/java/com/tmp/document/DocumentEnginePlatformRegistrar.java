package com.tmp.document;

import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.document.api.DocumentEngine;
import jakarta.annotation.PostConstruct;

final class DocumentEnginePlatformRegistrar {

    private final PlatformCore platformCore;
    private final DocumentEngine documentEngine;

    DocumentEnginePlatformRegistrar(PlatformCore platformCore, DocumentEngine documentEngine) {
        this.platformCore = platformCore;
        this.documentEngine = documentEngine;
    }

    @PostConstruct
    void registerDocumentEngineComponent() {
        if (!(documentEngine instanceof PlatformComponent platformComponent)) {
            throw new IllegalStateException("DocumentEngine bean must implement PlatformComponent");
        }
        platformCore.registerComponent(platformComponent);
    }
}
