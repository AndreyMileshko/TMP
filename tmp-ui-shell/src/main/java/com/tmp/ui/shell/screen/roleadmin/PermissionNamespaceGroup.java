package com.tmp.ui.shell.screen.roleadmin;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.PermissionSummary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Groups catalogue permissions by the stable {@link PermissionId} area prefix
 * ({@code area.resource.action}).
 */
public final class PermissionNamespaceGroup {

    private final String namespace;
    private final String displayName;
    private final List<PermissionSummary> permissions;

    public PermissionNamespaceGroup(String namespace, String displayName, List<PermissionSummary> permissions) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.permissions = List.copyOf(Objects.requireNonNull(permissions, "permissions"));
    }

    public String namespace() {
        return namespace;
    }

    public String displayName() {
        return displayName;
    }

    public List<PermissionSummary> permissions() {
        return permissions;
    }

    public static List<PermissionNamespaceGroup> group(List<PermissionSummary> catalogue) {
        Objects.requireNonNull(catalogue, "catalogue");
        Map<String, List<PermissionSummary>> byNs = new LinkedHashMap<>();
        for (PermissionSummary permission : catalogue) {
            String ns = namespaceOf(permission.permissionId());
            byNs.computeIfAbsent(ns, key -> new ArrayList<>()).add(permission);
        }
        List<PermissionNamespaceGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<PermissionSummary>> entry : byNs.entrySet()) {
            List<PermissionSummary> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(Comparator.comparing(
                    p -> p.displayName(), String.CASE_INSENSITIVE_ORDER));
            groups.add(new PermissionNamespaceGroup(
                    entry.getKey(), displayNameFor(entry.getKey()), sorted));
        }
        groups.sort(Comparator.comparingInt(PermissionNamespaceGroup::knownOrder)
                .thenComparing(PermissionNamespaceGroup::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(groups);
    }

    public static String namespaceOf(PermissionId permissionId) {
        Objects.requireNonNull(permissionId, "permissionId");
        String value = permissionId.value();
        int dot = value.indexOf('.');
        if (dot <= 0) {
            return "other";
        }
        return value.substring(0, dot);
    }

    public static String displayNameFor(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        return switch (namespace.toLowerCase(Locale.ROOT)) {
            case "security" -> "Администрирование";
            case "order" -> "Заказы";
            case "warehouse" -> "Склад";
            case "production" -> "Производство";
            case "cutting" -> "Раскрой";
            case "analytics" -> "Аналитика";
            case "other" -> "Прочее";
            default -> humanize(namespace);
        };
    }

    private static int knownOrder(PermissionNamespaceGroup group) {
        return switch (group.namespace().toLowerCase(Locale.ROOT)) {
            case "security" -> 0;
            case "order" -> 1;
            case "warehouse" -> 2;
            case "production" -> 3;
            case "cutting" -> 4;
            case "analytics" -> 5;
            default -> 100;
        };
    }

    private static String humanize(String namespace) {
        if (namespace.isBlank()) {
            return "Прочее";
        }
        String trimmed = namespace.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }
}
