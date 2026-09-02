package com.tmp.ui.shell.screen.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;

class SecurityAuditPresentationTest {

    @Test
    void formatsOccurredAtInExplicitZone() {
        Instant instant = Instant.parse("2026-09-02T02:47:15Z");
        assertEquals(
                "02.09.2026 09:47:15",
                SecurityAuditPresentation.formatOccurredAt(instant, ZoneId.of("Asia/Bangkok")));
    }

    @Test
    void mapsKnownOperationAndFallsBackForUnknown() {
        assertEquals("Создание пользователя", SecurityAuditPresentation.formatOperation("USER_CREATED"));
        assertEquals("CUSTOM_OPERATION", SecurityAuditPresentation.formatOperation("CUSTOM_OPERATION"));
    }

    @Test
    void formatsTargetAndResult() {
        assertEquals("Пользователь · admin", SecurityAuditPresentation.formatTarget("USER", "admin"));
        assertEquals("Роль", SecurityAuditPresentation.formatTarget("ROLE", null));
        assertEquals("UNKNOWN · x", SecurityAuditPresentation.formatTarget("UNKNOWN", "x"));
    }

    @Test
    void mapsResultTextAndBadgeStyle() {
        assertEquals("Успешно", SecurityAuditPresentation.formatResultText("SUCCESS"));
        assertEquals("Ошибка", SecurityAuditPresentation.formatResultText("FAILURE"));
        assertEquals("PENDING", SecurityAuditPresentation.formatResultText("PENDING"));
        assertEquals("tmp-badge-success", SecurityAuditPresentation.badgeStyleClass("SUCCESS"));
        assertEquals("tmp-badge-danger", SecurityAuditPresentation.badgeStyleClass("FAILURE"));
        assertEquals("tmp-badge-neutral", SecurityAuditPresentation.badgeStyleClass("PENDING"));
    }

    @Test
    void resultBadgeShowsHumanReadableText() {
        Label badge = SecurityAuditPresentation.createResultBadge("SUCCESS");
        assertEquals("Успешно", badge.getText());
        assertTrue(badge.getStyleClass().contains("tmp-badge"));
        assertTrue(badge.getStyleClass().contains("tmp-badge-success"));
    }
}
