package com.tmp.ui.shell.screen.production;

import java.util.Objects;

/** Read-only presentation row for Production history details. */
public final class ProductionHistoryRow {

    private final String occurredAtLabel;
    private final String operationLabel;
    private final String actorLabel;
    private final String descriptionLabel;

    public ProductionHistoryRow(
            String occurredAtLabel,
            String operationLabel,
            String actorLabel,
            String descriptionLabel) {
        this.occurredAtLabel = Objects.requireNonNull(occurredAtLabel, "occurredAtLabel");
        this.operationLabel = Objects.requireNonNull(operationLabel, "operationLabel");
        this.actorLabel = Objects.requireNonNull(actorLabel, "actorLabel");
        this.descriptionLabel = Objects.requireNonNull(descriptionLabel, "descriptionLabel");
    }

    public String occurredAtLabel() {
        return occurredAtLabel;
    }

    public String operationLabel() {
        return operationLabel;
    }

    public String actorLabel() {
        return actorLabel;
    }

    public String descriptionLabel() {
        return descriptionLabel;
    }
}
