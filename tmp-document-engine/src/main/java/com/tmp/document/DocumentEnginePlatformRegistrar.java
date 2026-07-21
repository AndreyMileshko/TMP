package com.tmp.document;

import com.tmp.core.api.PlatformCore;
import jakarta.annotation.PostConstruct;

final class DocumentEnginePlatformRegistrar {

    private final PlatformCore platformCore;
    private final DefaultDocumentEngine documentEngine;

    DocumentEnginePlatformRegistrar(PlatformCore platformCore, DefaultDocumentEngine documentEngine) {
        this.platformCore = platformCore;
        this.documentEngine = documentEngine;
    }

    @PostConstruct
    void registerDocumentEngineComponent() {
        platformCore.registerComponent(documentEngine);
    }
}
