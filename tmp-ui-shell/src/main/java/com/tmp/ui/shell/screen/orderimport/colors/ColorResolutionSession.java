package com.tmp.ui.shell.screen.orderimport.colors;

import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportPosition;
import com.tmp.order.api.imports.OrderImportSpecificationLine;
import com.tmp.order.api.imports.SpecificationColorConstraint;
import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.ColorImportResolutionApi;
import com.tmp.reference.api.ImportMappingSaveReport;
import com.tmp.reference.api.ResolvedColorPair;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory color resolution state for one import run (COL-040B). JavaFX-free; does not mutate
 * source batches.
 */
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "rows and source batches are defensive copies")
public final class ColorResolutionSession {

    /** Conflicting save intent for one normalized source key. */
    public record MappingSaveConflict(String sourceKey, int distinctPairCount) {}

    private final List<OrderImportBatch> sourceBatches;
    private final List<ColorResolutionRow> rows;
    private final List<ColorDto> activeColors;
    private final boolean globalSaveAllowed;
    private final SpecificationColorConstraint colorConstraint;
    private final ColorImportResolutionApi resolutionApi;

    ColorResolutionSession(
            List<OrderImportBatch> sourceBatches,
            List<ColorResolutionRow> rows,
            List<ColorDto> activeColors,
            boolean globalSaveAllowed,
            SpecificationColorConstraint colorConstraint,
            ColorImportResolutionApi resolutionApi) {
        this.sourceBatches = List.copyOf(Objects.requireNonNull(sourceBatches, "sourceBatches"));
        this.rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        this.activeColors = List.copyOf(Objects.requireNonNull(activeColors, "activeColors"));
        this.globalSaveAllowed = globalSaveAllowed;
        this.colorConstraint = Objects.requireNonNull(colorConstraint, "colorConstraint");
        this.resolutionApi = Objects.requireNonNull(resolutionApi, "resolutionApi");
    }

    public List<ColorResolutionRow> rows() {
        return rows;
    }

    public List<ColorDto> activeColors() {
        return activeColors;
    }

    public boolean globalSaveAllowed() {
        return globalSaveAllowed;
    }

    public boolean isReady() {
        for (ColorResolutionRow row : rows) {
            if (!row.isRowReady()) {
                return false;
            }
        }
        return true;
    }

    public ColorResolutionSession withSelection(Set<ColorResolutionRow.Id> selectedIds) {
        Objects.requireNonNull(selectedIds, "selectedIds");
        boolean changed = false;
        List<ColorResolutionRow> updated = new ArrayList<>(rows.size());
        for (ColorResolutionRow row : rows) {
            boolean highlight = selectedIds.contains(row.id());
            if (highlight != row.highlighted()) {
                changed = true;
                updated.add(row.withHighlight(highlight));
            } else {
                updated.add(row);
            }
        }
        if (!changed) {
            return this;
        }
        return new ColorResolutionSession(
                sourceBatches, updated, activeColors, globalSaveAllowed, colorConstraint, resolutionApi);
    }

    public ColorResolutionSession clearSelection() {
        return withSelection(Set.of());
    }

    public ColorResolutionSession applyCatalogPairToHighlighted(UUID insideColorId, UUID outsideColorId) {
        return applyCatalogPair(rows.stream().filter(ColorResolutionRow::highlighted).map(ColorResolutionRow::id).toList(),
                insideColorId,
                outsideColorId);
    }

    public ColorResolutionSession applyCatalogPairToSourceKey(String sourceKey, UUID insideColorId, UUID outsideColorId) {
        Objects.requireNonNull(sourceKey, "sourceKey");
        List<ColorResolutionRow.Id> ids = new ArrayList<>();
        for (ColorResolutionRow row : rows) {
            if (sourceKey.equals(row.sourceKey())) {
                ids.add(row.id());
            }
        }
        return applyCatalogPair(ids, insideColorId, outsideColorId);
    }

    public ColorResolutionSession applyCatalogPair(
            List<ColorResolutionRow.Id> targetIds, UUID insideColorId, UUID outsideColorId) {
        Objects.requireNonNull(targetIds, "targetIds");
        if (targetIds.isEmpty()) {
            return this;
        }
        ResolvedColorPair pair;
        try {
            pair = resolutionApi.composePair(insideColorId, outsideColorId);
        } catch (RuntimeException ex) {
            return this;
        }
        SpecificationColorConstraint.Result check = colorConstraint.check(pair.displayColor());
        Set<ColorResolutionRow.Id> targetSet = new HashSet<>(targetIds);
        List<ColorResolutionRow> updated = new ArrayList<>(rows.size());
        for (ColorResolutionRow row : rows) {
            if (targetSet.contains(row.id())) {
                updated.add(row.withCatalogPair(pair, check, globalSaveAllowed));
            } else {
                updated.add(row);
            }
        }
        return new ColorResolutionSession(
                sourceBatches, updated, activeColors, globalSaveAllowed, colorConstraint, resolutionApi);
    }

    public ColorResolutionSession keepSourceTextForHighlighted() {
        List<ColorResolutionRow.Id> ids =
                rows.stream().filter(ColorResolutionRow::highlighted).map(ColorResolutionRow::id).toList();
        return keepSourceTextForIds(ids);
    }

    public ColorResolutionSession keepSourceTextForAllNeedsChoice() {
        List<ColorResolutionRow.Id> ids = new ArrayList<>();
        for (ColorResolutionRow row : rows) {
            if (row.userState() == ColorResolutionRow.UserState.NEEDS_CHOICE) {
                ids.add(row.id());
            }
        }
        return keepSourceTextForIds(ids);
    }

    private ColorResolutionSession keepSourceTextForIds(List<ColorResolutionRow.Id> targetIds) {
        if (targetIds.isEmpty()) {
            return this;
        }
        Set<ColorResolutionRow.Id> targetSet = new HashSet<>(targetIds);
        List<ColorResolutionRow> updated = new ArrayList<>(rows.size());
        for (ColorResolutionRow row : rows) {
            if (targetSet.contains(row.id())) {
                updated.add(row.withSourceText(colorConstraint.check(row.sourceText())));
            } else {
                updated.add(row);
            }
        }
        return new ColorResolutionSession(
                sourceBatches, updated, activeColors, globalSaveAllowed, colorConstraint, resolutionApi);
    }

    public ColorResolutionSession applyCustomColorToHighlighted(String customColor) {
        List<ColorResolutionRow.Id> ids =
                rows.stream().filter(ColorResolutionRow::highlighted).map(ColorResolutionRow::id).toList();
        return applyCustomColor(ids, customColor);
    }

    public ColorResolutionSession applyCustomColor(List<ColorResolutionRow.Id> targetIds, String customColor) {
        Objects.requireNonNull(targetIds, "targetIds");
        if (targetIds.isEmpty()) {
            return this;
        }
        SpecificationColorConstraint.Result check = colorConstraint.check(customColor);
        Set<ColorResolutionRow.Id> targetSet = new HashSet<>(targetIds);
        List<ColorResolutionRow> updated = new ArrayList<>(rows.size());
        for (ColorResolutionRow row : rows) {
            if (targetSet.contains(row.id())) {
                updated.add(row.withCustomColor(customColor, check));
            } else {
                updated.add(row);
            }
        }
        return new ColorResolutionSession(
                sourceBatches, updated, activeColors, globalSaveAllowed, colorConstraint, resolutionApi);
    }

    public ColorResolutionSession setSaveMappingForAvailableHighlighted(boolean selected) {
        List<ColorResolutionRow> updated = new ArrayList<>(rows.size());
        boolean changed = false;
        for (ColorResolutionRow row : rows) {
            if (row.highlighted() && row.saveMappingAvailable()) {
                ColorResolutionRow next = row.withSaveMappingSelected(selected);
                updated.add(next);
                changed = true;
            } else {
                updated.add(row);
            }
        }
        if (!changed) {
            return this;
        }
        return new ColorResolutionSession(
                sourceBatches, updated, activeColors, globalSaveAllowed, colorConstraint, resolutionApi);
    }

    public List<ImportMappingSaveReport.Candidate> mappingSaveCandidates() {
        Map<String, ImportMappingSaveReport.Candidate> byKey = new LinkedHashMap<>();
        Set<String> conflictKeys = new HashSet<>();
        for (MappingSaveConflict conflict : mappingSaveConflicts()) {
            conflictKeys.add(conflict.sourceKey());
        }
        for (ColorResolutionRow row : rows) {
            if (!row.saveMappingAvailable() || !row.saveMappingSelected()) {
                continue;
            }
            if (conflictKeys.contains(row.sourceKey())) {
                continue;
            }
            ResolvedColorPair pair = row.catalogPair();
            if (pair == null) {
                continue;
            }
            ImportMappingSaveReport.Candidate candidate =
                    new ImportMappingSaveReport.Candidate(row.sourceText(), pair.insideColorId(), pair.outsideColorId());
            ImportMappingSaveReport.Candidate existing = byKey.get(row.sourceKey());
            if (existing == null) {
                byKey.put(row.sourceKey(), candidate);
                continue;
            }
            if (!samePair(existing, candidate)) {
                conflictKeys.add(row.sourceKey());
                byKey.remove(row.sourceKey());
            }
        }
        return List.copyOf(byKey.values());
    }

    public List<MappingSaveConflict> mappingSaveConflicts() {
        Map<String, Set<PairSignature>> pairsByKey = new HashMap<>();
        for (ColorResolutionRow row : rows) {
            if (!row.saveMappingAvailable() || !row.saveMappingSelected()) {
                continue;
            }
            ResolvedColorPair pair = row.catalogPair();
            if (pair == null) {
                continue;
            }
            pairsByKey
                    .computeIfAbsent(row.sourceKey(), key -> new HashSet<>())
                    .add(new PairSignature(pair.insideColorId(), pair.outsideColorId()));
        }
        List<MappingSaveConflict> conflicts = new ArrayList<>();
        for (Map.Entry<String, Set<PairSignature>> entry : pairsByKey.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflicts.add(new MappingSaveConflict(entry.getKey(), entry.getValue().size()));
            }
        }
        return List.copyOf(conflicts);
    }

    private static boolean samePair(ImportMappingSaveReport.Candidate left, ImportMappingSaveReport.Candidate right) {
        return left.insideColorId().equals(right.insideColorId())
                && left.outsideColorId().equals(right.outsideColorId());
    }

    private record PairSignature(UUID insideColorId, UUID outsideColorId) {}

    public List<OrderImportBatch> rebuild() {
        if (!isReady()) {
            throw new IllegalStateException("Color resolution session is not ready for rebuild");
        }
        Map<ColorResolutionRow.Id, String> finalColors = new HashMap<>();
        for (ColorResolutionRow row : rows) {
            finalColors.put(row.id(), row.finalColor());
        }
        List<OrderImportBatch> rebuilt = new ArrayList<>(sourceBatches.size());
        for (int batchIndex = 0; batchIndex < sourceBatches.size(); batchIndex++) {
            OrderImportBatch batch = sourceBatches.get(batchIndex);
            List<OrderImportPosition> rebuiltPositions = new ArrayList<>(batch.positions().size());
            for (int positionIndex = 0; positionIndex < batch.positions().size(); positionIndex++) {
                OrderImportPosition position = batch.positions().get(positionIndex);
                List<OrderImportSpecificationLine> rebuiltLines = new ArrayList<>(position.specificationLines().size());
                for (int lineIndex = 0; lineIndex < position.specificationLines().size(); lineIndex++) {
                    OrderImportSpecificationLine line = position.specificationLines().get(lineIndex);
                    ColorResolutionRow.Id id = new ColorResolutionRow.Id(batchIndex, positionIndex, lineIndex);
                    String color = finalColors.getOrDefault(id, line.color());
                    rebuiltLines.add(
                            OrderImportSpecificationLine.of(
                                    line.materialCode(),
                                    line.materialName(),
                                    color,
                                    line.length(),
                                    line.unitOfMeasure(),
                                    line.quantity()));
                }
                rebuiltPositions.add(
                        OrderImportPosition.of(
                                position.externalPositionNumber(),
                                position.productCode(),
                                position.name(),
                                position.quantity(),
                                rebuiltLines));
            }
            rebuilt.add(
                    OrderImportBatch.of(
                            batch.sourceType(),
                            batch.sourceReference(),
                            batch.contentChecksum(),
                            batch.orderNumber(),
                            batch.orderDate(),
                            batch.readyDate(),
                            batch.customerName(),
                            rebuiltPositions));
        }
        return List.copyOf(rebuilt);
    }
}