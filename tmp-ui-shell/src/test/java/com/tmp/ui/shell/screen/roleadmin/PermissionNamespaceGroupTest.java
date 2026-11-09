package com.tmp.ui.shell.screen.roleadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.PermissionSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionNamespaceGroupTest {

    @Test
    void groupsKnownNamespacesAndKeepsUnknownVisible() {
        List<PermissionNamespaceGroup> groups =
                PermissionNamespaceGroup.group(
                        List.of(
                                summary("order.order.view", "Просмотр заказов"),
                                summary("warehouse.stock.view", "Просмотр склада"),
                                summary("production.order.view", "Просмотр производства"),
                                summary("reference.color.view", "Просмотр справочника цветов"),
                                summary("security.roles.view", "Просмотр ролей"),
                                summary("cutting.plan.view", "Просмотр раскроя"),
                                summary("analytics.report.view", "Отчёты"),
                                summary("custom.thing.do", "Кастомное право")));
        assertEquals("Администрирование", groups.get(0).displayName());
        assertEquals("Заказы", groups.get(1).displayName());
        assertEquals("Склад", groups.get(2).displayName());
        assertEquals("Производство", groups.get(3).displayName());
        assertEquals("Справочники", groups.get(4).displayName());
        assertEquals("Раскрой", groups.get(5).displayName());
        assertEquals("Аналитика", groups.get(6).displayName());
        assertTrue(groups.stream().anyMatch(g -> g.displayName().equals("Custom")
                || g.namespace().equals("custom")));
        int total = groups.stream().mapToInt(g -> g.permissions().size()).sum();
        assertEquals(8, total);
    }

    @Test
    void referenceNamespaceDisplaysAsDirectoriesLabel() {
        assertEquals("Справочники", PermissionNamespaceGroup.displayNameFor("reference"));
        assertEquals("reference", PermissionNamespaceGroup.namespaceOf(
                PermissionId.of("reference.color-mapping.edit")));
    }

    private static PermissionSummary summary(String id, String display) {
        return new PermissionSummary(PermissionId.of(id), display, "", true);
    }
}
