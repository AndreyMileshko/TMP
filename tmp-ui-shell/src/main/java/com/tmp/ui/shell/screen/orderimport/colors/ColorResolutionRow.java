package com.tmp.ui.shell.screen.orderimport.colors;

import com.tmp.order.api.imports.SpecificationColorConstraint;
import com.tmp.reference.api.ResolvedColorPair;
import com.tmp.reference.api.SourceColorResolution;
import java.util.Objects;

/**
 * One import specification line that carries a non-empty SO7 color text (COL-040B). Immutable row
 * snapshot with functional updates for session operations.
 */
public final class ColorResolutionRow {

    /** Stable pointer to a specification line inside the parsed import structure. */
    public record Id(int batchIndex, int positionIndex, int lineIndex) {

        public Id {
            if (batchIndex < 0 || positionIndex < 0 || lineIndex < 0) {
                throw new IllegalArgumentException("indices must be non-negative");
            }
        }
    }

    /** User-facing resolution state (TMP-SPEC-015 section 8). */
    public enum UserState {
        MATCHED,
        NEEDS_CHOICE,
        MANUALLY_CHANGED
    }

    /** How the final color was chosen. */
    public enum ChoiceMode {
        NONE,
        CATALOG_PAIR,
        SOURCE_TEXT,
        CUSTOM_COLOR
    }

    /** Snapshot of automatic prefill used to detect manual changes. */
    public record AutoSnapshot(
            UserState userState,
            ChoiceMode choiceMode,
            ResolvedColorPair catalogPair,
            String customColor,
            String finalColor) {}

    private final Id id;
    private final String orderNumber;
    private final String externalPositionNumber;
    private final String productCode;
    private final String productName;
    private final int specificationLineNumber;
    private final String materialCode;
    private final String materialName;
    private final String sourceText;
    private final String sourceKey;
    private final SourceColorResolution.Status lookupStatus;
    private final AutoSnapshot autoSnapshot;
    private final UserState userState;
    private final ChoiceMode choiceMode;
    private final ResolvedColorPair catalogPair;
    private final String customColor;
    private final String finalColor;
    private final SpecificationColorConstraint.Result constraintResult;
    private final boolean saveMappingAvailable;
    private final boolean saveMappingSelected;
    private final boolean highlighted;

    private ColorResolutionRow(
            Id id,
            String orderNumber,
            String externalPositionNumber,
            String productCode,
            String productName,
            int specificationLineNumber,
            String materialCode,
            String materialName,
            String sourceText,
            String sourceKey,
            SourceColorResolution.Status lookupStatus,
            AutoSnapshot autoSnapshot,
            UserState userState,
            ChoiceMode choiceMode,
            ResolvedColorPair catalogPair,
            String customColor,
            String finalColor,
            SpecificationColorConstraint.Result constraintResult,
            boolean saveMappingAvailable,
            boolean saveMappingSelected,
            boolean highlighted) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderNumber = orderNumber;
        this.externalPositionNumber = externalPositionNumber;
        this.productCode = productCode;
        this.productName = productName;
        this.specificationLineNumber = specificationLineNumber;
        this.materialCode = materialCode;
        this.materialName = materialName;
        this.sourceText = Objects.requireNonNull(sourceText, "sourceText");
        this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
        this.lookupStatus = Objects.requireNonNull(lookupStatus, "lookupStatus");
        this.autoSnapshot = Objects.requireNonNull(autoSnapshot, "autoSnapshot");
        this.userState = Objects.requireNonNull(userState, "userState");
        this.choiceMode = Objects.requireNonNull(choiceMode, "choiceMode");
        this.catalogPair = catalogPair;
        this.customColor = customColor;
        this.finalColor = finalColor;
        this.constraintResult = constraintResult;
        this.saveMappingAvailable = saveMappingAvailable;
        this.saveMappingSelected = saveMappingSelected;
        this.highlighted = highlighted;
    }

    public static ColorResolutionRow createAutomatic(
            Id id,
            String orderNumber,
            String externalPositionNumber,
            String productCode,
            String productName,
            int specificationLineNumber,
            String materialCode,
            String materialName,
            String sourceText,
            String sourceKey,
            SourceColorResolution.Status lookupStatus,
            ResolvedColorPair matchedPair,
            String finalColor,
            SpecificationColorConstraint.Result constraintResult,
            boolean globalSaveAllowed) {
        UserState userState;
        ChoiceMode choiceMode;
        ResolvedColorPair pair;
        if (lookupStatus == SourceColorResolution.Status.MATCHED && matchedPair != null) {
            userState = UserState.MATCHED;
            choiceMode = ChoiceMode.CATALOG_PAIR;
            pair = matchedPair;
        } else {
            userState = UserState.NEEDS_CHOICE;
            choiceMode = ChoiceMode.NONE;
            pair = null;
            finalColor = null;
            constraintResult = null;
        }
        boolean saveAvailable = computeSaveAvailable(globalSaveAllowed, lookupStatus, choiceMode, pair);
        AutoSnapshot snapshot =
                new AutoSnapshot(userState, choiceMode, pair, null, finalColor);
        return new ColorResolutionRow(
                id,
                orderNumber,
                externalPositionNumber,
                productCode,
                productName,
                specificationLineNumber,
                materialCode,
                materialName,
                sourceText,
                sourceKey,
                lookupStatus,
                snapshot,
                userState,
                choiceMode,
                pair,
                null,
                finalColor,
                constraintResult,
                saveAvailable,
                saveAvailable,
                false);
    }

    private static boolean computeSaveAvailable(
            boolean globalSaveAllowed,
            SourceColorResolution.Status lookupStatus,
            ChoiceMode choiceMode,
            ResolvedColorPair pair) {
        if (!globalSaveAllowed) {
            return false;
        }
        if (lookupStatus != SourceColorResolution.Status.NO_MAPPING) {
            return false;
        }
        return choiceMode == ChoiceMode.CATALOG_PAIR && pair != null;
    }

    public boolean isRowReady() {
        return finalColor != null
                && constraintResult != null
                && constraintResult.isAllowed();
    }

    public ColorResolutionRow withHighlight(boolean highlighted) {
        if (this.highlighted == highlighted) {
            return this;
        }
        return copyWith(highlighted, saveMappingSelected);
    }

    public ColorResolutionRow withSaveMappingSelected(boolean selected) {
        if (!saveMappingAvailable) {
            return this;
        }
        return copyWith(highlighted, selected);
    }

    public ColorResolutionRow recomputeSaveAvailability(boolean globalSaveAllowed) {
        boolean available = computeSaveAvailable(globalSaveAllowed, lookupStatus, choiceMode, catalogPair);
        boolean selected;
        if (!available) {
            selected = false;
        } else if (saveMappingAvailable && !saveMappingSelected) {
            selected = false;
        } else {
            selected = available;
        }
        if (available == saveMappingAvailable && selected == saveMappingSelected) {
            return this;
        }
        return new ColorResolutionRow(
                id,
                orderNumber,
                externalPositionNumber,
                productCode,
                productName,
                specificationLineNumber,
                materialCode,
                materialName,
                sourceText,
                sourceKey,
                lookupStatus,
                autoSnapshot,
                userState,
                choiceMode,
                catalogPair,
                customColor,
                finalColor,
                constraintResult,
                available,
                selected,
                highlighted);
    }

    public ColorResolutionRow withCatalogPair(
            ResolvedColorPair pair,
            SpecificationColorConstraint.Result check,
            boolean globalSaveAllowed) {
        Objects.requireNonNull(pair, "pair");
        Objects.requireNonNull(check, "check");
        UserState state = resolveUserState(ChoiceMode.CATALOG_PAIR, pair, null, pair.displayColor());
        boolean saveAvailable = computeSaveAvailable(globalSaveAllowed, lookupStatus, ChoiceMode.CATALOG_PAIR, pair);
        boolean saveSelected = saveAvailabilityDefault(saveAvailable);
        return new ColorResolutionRow(
                id,
                orderNumber,
                externalPositionNumber,
                productCode,
                productName,
                specificationLineNumber,
                materialCode,
                materialName,
                sourceText,
                sourceKey,
                lookupStatus,
                autoSnapshot,
                state,
                ChoiceMode.CATALOG_PAIR,
                pair,
                null,
                pair.displayColor(),
                check,
                saveAvailable,
                saveSelected,
                highlighted);
    }

    public ColorResolutionRow withSourceText(SpecificationColorConstraint.Result check) {
        Objects.requireNonNull(check, "check");
        UserState state = resolveUserState(ChoiceMode.SOURCE_TEXT, null, null, sourceText);
        return new ColorResolutionRow(
                id,
                orderNumber,
                externalPositionNumber,
                productCode,
                productName,
                specificationLineNumber,
                materialCode,
                materialName,
                sourceText,
                sourceKey,
                lookupStatus,
                autoSnapshot,
                state,
                ChoiceMode.SOURCE_TEXT,
                null,
                null,
                sourceText,
                check,
                false,
                false,
                highlighted);
    }

    public ColorResolutionRow withCustomColor(String custom, SpecificationColorConstraint.Result check) {
        Objects.requireNonNull(check, "check");
        UserState state = resolveUserState(ChoiceMode.CUSTOM_COLOR, null, custom, custom);
        String resolvedFinal = custom;
        return new ColorResolutionRow(
                id,
                orderNumber,
                externalPositionNumber,
                productCode,
                productName,
                specificationLineNumber,
                materialCode,
                materialName,
                sourceText,
                sourceKey,
                lookupStatus,
                autoSnapshot,
                state,
                ChoiceMode.CUSTOM_COLOR,
                null,
                custom,
                resolvedFinal,
                check,
                false,
                false,
                highlighted);
    }

    private UserState resolveUserState(
            ChoiceMode mode, ResolvedColorPair pair, String custom, String finalVal) {
        if (matchesAuto(mode, pair, custom, finalVal)) {
            return autoSnapshot.userState();
        }
        if (autoSnapshot.userState() == UserState.NEEDS_CHOICE) {
            return UserState.MANUALLY_CHANGED;
        }
        return UserState.MANUALLY_CHANGED;
    }

    private boolean matchesAuto(
            ChoiceMode mode, ResolvedColorPair pair, String custom, String finalVal) {
        if (mode != autoSnapshot.choiceMode()) {
            return false;
        }
        return switch (mode) {
            case NONE -> finalVal == null && autoSnapshot.finalColor() == null;
            case CATALOG_PAIR ->
                    pair != null
                            && autoSnapshot.catalogPair() != null
                            && pair.insideColorId().equals(autoSnapshot.catalogPair().insideColorId())
                            && pair.outsideColorId().equals(autoSnapshot.catalogPair().outsideColorId());
            case SOURCE_TEXT -> Objects.equals(sourceText, autoSnapshot.finalColor());
            case CUSTOM_COLOR -> Objects.equals(custom, autoSnapshot.customColor());
        };
    }

    private boolean saveAvailabilityDefault(boolean available) {
        if (!available) {
            return false;
        }
        if (saveMappingAvailable && !saveMappingSelected) {
            return false;
        }
        return true;
    }

    private ColorResolutionRow copyWith(boolean highlighted, boolean saveMappingSelected) {
        return new ColorResolutionRow(
                id,
                orderNumber,
                externalPositionNumber,
                productCode,
                productName,
                specificationLineNumber,
                materialCode,
                materialName,
                sourceText,
                sourceKey,
                lookupStatus,
                autoSnapshot,
                userState,
                choiceMode,
                catalogPair,
                customColor,
                finalColor,
                constraintResult,
                saveMappingAvailable,
                saveMappingSelected,
                highlighted);
    }

    public Id id() {
        return id;
    }

    public String orderNumber() {
        return orderNumber;
    }

    public String externalPositionNumber() {
        return externalPositionNumber;
    }

    public String productCode() {
        return productCode;
    }

    public String productName() {
        return productName;
    }

    public int specificationLineNumber() {
        return specificationLineNumber;
    }

    public String materialCode() {
        return materialCode;
    }

    public String materialName() {
        return materialName;
    }

    public String sourceText() {
        return sourceText;
    }

    public String sourceKey() {
        return sourceKey;
    }

    public SourceColorResolution.Status lookupStatus() {
        return lookupStatus;
    }

    public AutoSnapshot autoSnapshot() {
        return autoSnapshot;
    }

    public UserState userState() {
        return userState;
    }

    public ChoiceMode choiceMode() {
        return choiceMode;
    }

    public ResolvedColorPair catalogPair() {
        return catalogPair;
    }

    public String customColor() {
        return customColor;
    }

    public String finalColor() {
        return finalColor;
    }

    public SpecificationColorConstraint.Result constraintResult() {
        return constraintResult;
    }

    public boolean saveMappingAvailable() {
        return saveMappingAvailable;
    }

    public boolean saveMappingSelected() {
        return saveMappingSelected;
    }

    public boolean highlighted() {
        return highlighted;
    }
}