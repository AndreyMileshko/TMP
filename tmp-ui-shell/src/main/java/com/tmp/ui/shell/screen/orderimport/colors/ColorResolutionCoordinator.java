package com.tmp.ui.shell.screen.orderimport.colors;

import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportPosition;
import com.tmp.order.api.imports.OrderImportSpecificationLine;
import com.tmp.order.api.imports.SpecificationColorConstraint;
import com.tmp.reference.api.ColorImportResolutionApi;
import com.tmp.reference.api.SourceColorResolution;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Prepares STXT import batches for color resolution when automatic matching is ON (COL-040B).
 */
public final class ColorResolutionCoordinator {

    /**
     * Outcome of {@link #prepare(List)}. {@code readyBatches} preserves caller list identity on OFF
     * (import compatibility); rebuilt lists are already immutable copies from the session.
     */
    @SuppressFBWarnings(
            value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
            justification = "OFF path intentionally returns the original batch list reference.")
    public record Preparation(
            boolean automaticMatchingEnabled,
            boolean needsResolutionWindow,
            ColorResolutionSession session,
            List<OrderImportBatch> readyBatches) {}

    private static final class LineRef {
        private final ColorResolutionRow.Id id;
        private final String orderNumber;
        private final String externalPositionNumber;
        private final String productCode;
        private final String productName;
        private final int specificationLineNumber;
        private final String materialCode;
        private final String materialName;
        private final String sourceText;

        private LineRef(
                ColorResolutionRow.Id id,
                String orderNumber,
                String externalPositionNumber,
                String productCode,
                String productName,
                int specificationLineNumber,
                String materialCode,
                String materialName,
                String sourceText) {
            this.id = id;
            this.orderNumber = orderNumber;
            this.externalPositionNumber = externalPositionNumber;
            this.productCode = productCode;
            this.productName = productName;
            this.specificationLineNumber = specificationLineNumber;
            this.materialCode = materialCode;
            this.materialName = materialName;
            this.sourceText = sourceText;
        }
    }

    private final ColorImportResolutionApi resolutionApi;
    private final SpecificationColorConstraint colorConstraint;

    public ColorResolutionCoordinator(
            ColorImportResolutionApi resolutionApi, SpecificationColorConstraint colorConstraint) {
        this.resolutionApi = Objects.requireNonNull(resolutionApi, "resolutionApi");
        this.colorConstraint = Objects.requireNonNull(colorConstraint, "colorConstraint");
    }

    public Preparation prepare(List<OrderImportBatch> batches) {
        Objects.requireNonNull(batches, "batches");
        if (!resolutionApi.isAutomaticMatchingEnabled()) {
            return new Preparation(false, false, null, batches);
        }
        List<LineRef> coloredLines = collectColoredLines(batches);
        if (coloredLines.isEmpty()) {
            ColorResolutionSession session =
                    new ColorResolutionSession(
                            batches,
                            List.of(),
                            resolutionApi.listActiveColors(),
                            resolutionApi.canSaveGlobalMappings(),
                            colorConstraint,
                            resolutionApi);
            return new Preparation(true, false, session, batches);
        }
        List<String> distinctTexts = collectDistinctSourceTexts(coloredLines);
        List<SourceColorResolution> resolutions = resolutionApi.resolveSourceTexts(distinctTexts);
        if (resolutions.size() != distinctTexts.size()) {
            throw new IllegalStateException("Color resolution result count does not match request");
        }
        Map<String, SourceColorResolution> bySourceText = new LinkedHashMap<>();
        for (int i = 0; i < distinctTexts.size(); i++) {
            SourceColorResolution resolution = resolutions.get(i);
            if (!distinctTexts.get(i).equals(resolution.sourceText())) {
                throw new IllegalStateException("Color resolution result order does not match request");
            }
            bySourceText.put(distinctTexts.get(i), resolution);
        }
        boolean globalSave = resolutionApi.canSaveGlobalMappings();
        List<ColorResolutionRow> rows = new ArrayList<>(coloredLines.size());
        for (LineRef line : coloredLines) {
            SourceColorResolution resolution = Objects.requireNonNull(bySourceText.get(line.sourceText));
            rows.add(buildAutomaticRow(line, resolution, globalSave));
        }
        ColorResolutionSession session =
                new ColorResolutionSession(
                        batches,
                        rows,
                        resolutionApi.listActiveColors(),
                        globalSave,
                        colorConstraint,
                        resolutionApi);
        boolean needsWindow = !session.isReady();
        List<OrderImportBatch> ready = needsWindow ? null : session.rebuild();
        return new Preparation(true, needsWindow, session, ready);
    }

    private static List<LineRef> collectColoredLines(List<OrderImportBatch> batches) {
        List<LineRef> lines = new ArrayList<>();
        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            OrderImportBatch batch = batches.get(batchIndex);
            for (int positionIndex = 0; positionIndex < batch.positions().size(); positionIndex++) {
                OrderImportPosition position = batch.positions().get(positionIndex);
                for (int lineIndex = 0; lineIndex < position.specificationLines().size(); lineIndex++) {
                    OrderImportSpecificationLine specLine = position.specificationLines().get(lineIndex);
                    String color = specLine.color();
                    if (color == null || color.isBlank()) {
                        continue;
                    }
                    lines.add(
                            new LineRef(
                                    new ColorResolutionRow.Id(batchIndex, positionIndex, lineIndex),
                                    batch.orderNumber(),
                                    position.externalPositionNumber(),
                                    position.productCode(),
                                    position.name(),
                                    lineIndex + 1,
                                    specLine.materialCode(),
                                    specLine.materialName(),
                                    color));
                }
            }
        }
        return lines;
    }

    private static List<String> collectDistinctSourceTexts(List<LineRef> lines) {
        Map<String, String> distinct = new LinkedHashMap<>();
        for (LineRef line : lines) {
            distinct.putIfAbsent(line.sourceText, line.sourceText);
        }
        return List.copyOf(distinct.values());
    }

    private ColorResolutionRow buildAutomaticRow(
            LineRef line, SourceColorResolution resolution, boolean globalSaveAllowed) {
        String finalColor = null;
        SpecificationColorConstraint.Result check = null;
        if (resolution.status() == SourceColorResolution.Status.MATCHED && resolution.pair() != null) {
            finalColor = resolution.pair().displayColor();
            check = colorConstraint.check(finalColor);
        }
        return ColorResolutionRow.createAutomatic(
                line.id,
                line.orderNumber,
                line.externalPositionNumber,
                line.productCode,
                line.productName,
                line.specificationLineNumber,
                line.materialCode,
                line.materialName,
                line.sourceText,
                resolution.sourceKey(),
                resolution.status(),
                resolution.pair(),
                finalColor,
                check,
                globalSaveAllowed);
    }
}