package com.tmp.ui.shell.screen.orderimport.colors;

import com.tmp.reference.api.ImportMappingSaveReport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** User-facing summaries for post-confirm global mapping save (TMP-SPEC-015 §11). */
public final class ImportMappingSaveMessages {

    private ImportMappingSaveMessages() {}

    public static String formatSuccessfulSaveSummary(
            ImportMappingSaveReport report, ColorResolutionSession session) {
        Objects.requireNonNull(report, "report");
        List<RejectedKey> rejected = collectRejectedKeys(report, session);
        if (report.savedCount() == 0 && rejected.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        text.append("Сопоставлений сохранено: ").append(report.savedCount()).append('.');
        if (!rejected.isEmpty()) {
            text.append("\nНе сохранены:");
            for (RejectedKey entry : rejected) {
                text.append("\n• ").append(entry.sourceText()).append(" — ").append(entry.reason());
            }
        }
        return text.toString();
    }

    public static String technicalFailureMessage(Throwable error) {
        String reason = readableTechnicalReason(error);
        return "Заказ импортирован, но сопоставления цветов не сохранены: "
                + reason
                + ". Их можно добавить в Справочники → Цвета.";
    }

  /** Appendix when import success text already states the order was imported. */
    public static String technicalFailureAppendix(Throwable error) {
        String reason = readableTechnicalReason(error);
        return "Сопоставления цветов не сохранены: "
                + reason
                + ". Их можно добавить в Справочники → Цвета.";
    }

    private static List<RejectedKey> collectRejectedKeys(
            ImportMappingSaveReport report, ColorResolutionSession session) {
        Map<String, RejectedKey> byKey = new LinkedHashMap<>();
        for (ImportMappingSaveReport.Entry entry : report.entries()) {
            if (entry.result() == ImportMappingSaveReport.Result.SAVED) {
                continue;
            }
            byKey.put(
                    entry.sourceKey(),
                    new RejectedKey(entry.sourceText(), resultLabel(entry.result())));
        }
        if (session != null) {
            for (ColorResolutionSession.MappingSaveConflict conflict : session.mappingSaveConflicts()) {
                byKey.putIfAbsent(
                        conflict.sourceKey(),
                        new RejectedKey(conflict.sourceKey(), resultLabel(ImportMappingSaveReport.Result.NOT_SAVED_CONFLICT_IN_IMPORT)));
            }
        }
        return new ArrayList<>(byKey.values());
    }

    private static String resultLabel(ImportMappingSaveReport.Result result) {
        return switch (result) {
            case NOT_SAVED_MAPPING_EXISTS -> "соответствие уже существует";
            case NOT_SAVED_CONFLICT_IN_IMPORT -> "конфликт в импорте";
            case NOT_SAVED_COLOR_INACTIVE -> "цвет архивирован";
            case NOT_SAVED_INVALID_SOURCE -> "недопустимый исходный текст";
            case SAVED -> "сохранено";
        };
    }

    private static String readableTechnicalReason(Throwable error) {
        if (error == null) {
            return "техническая ошибка";
        }
        String message = error.getMessage();
        if (message != null && !message.isBlank()) {
            return message.trim();
        }
        return error.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    private record RejectedKey(String sourceText, String reason) {}
}