package com.tmp.bootstrap;

import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.api.CapabilityEngineStatus;
import com.tmp.core.api.PlatformCore;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentQuery;
import com.tmp.ui.shell.JavaFxShellLauncher;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class DesktopBootstrap {

    private DesktopBootstrap() {
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext springContext = new SpringApplicationBuilder(TmpBootstrapApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
        PlatformCore platformCore = springContext.getBean(PlatformCore.class);
        DocumentEngine documentEngine = springContext.getBean(DocumentEngine.class);
        CapabilityEngine capabilityEngine = springContext.getBean(CapabilityEngine.class);
        JavaFxShellLauncher.launch(
                springContext::close,
                platformCore.status().summary() + "\n" + formatCapabilityStatus(capabilityEngine),
                formatDocumentPanel(documentEngine));
    }

    static String formatCapabilityStatus(CapabilityEngine capabilityEngine) {
        CapabilityEngineStatus status = capabilityEngine.status();
        StringBuilder builder = new StringBuilder("Capability Engine\n");
        builder.append("discovered=")
                .append(status.discoveredCount())
                .append(" active=")
                .append(status.activeCount())
                .append('\n');
        for (CapabilityDescriptor descriptor : capabilityEngine.registeredCapabilities()) {
            builder.append(descriptor.id().value())
                    .append(" state=")
                    .append(capabilityEngine.stateOf(descriptor.id()))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    static String formatDocumentPanel(DocumentEngine documentEngine) {
        List<DocumentMetadata> documents = documentEngine.search(DocumentQuery.all(5));
        if (documents.isEmpty()) {
            return "Document Engine ready\nNo documents yet";
        }
        StringBuilder builder = new StringBuilder("Document Engine\n");
        for (DocumentMetadata document : documents) {
            builder.append(document.documentNumber())
                    .append(" | ")
                    .append(document.title())
                    .append(" | ")
                    .append(document.status())
                    .append('\n');
        }
        return builder.toString().trim();
    }
}
