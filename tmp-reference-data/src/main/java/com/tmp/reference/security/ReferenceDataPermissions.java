package com.tmp.reference.security;

import com.tmp.security.api.PermissionId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable catalogue of Reference Data permission identifiers (TMP-SPEC-015 §15).
 *
 * <p>Logical shorthand names map to {@link PermissionId} codes in the mandatory {@code
 * <area>.<resource>.<action>} format. Hyphen is allowed in the resource segment ({@code
 * color-mapping}, {@code color-setting}). This catalogue does not assign permissions to users or
 * roles.
 */
public final class ReferenceDataPermissions {

    /** Просмотр каталога цветов TMP, соответствий СО7 и состояния переключателя. */
    public static final PermissionId COLOR_VIEW = PermissionId.of("reference.color.view");

    /** Создание, архивирование и восстановление цветов TMP. */
    public static final PermissionId COLOR_EDIT = PermissionId.of("reference.color.edit");

    /** Управление глобальными соответствиями СО7, включая сохранение из окна импорта. */
    public static final PermissionId COLOR_MAPPING_EDIT =
            PermissionId.of("reference.color-mapping.edit");

    /** Изменение общесистемного переключателя автоматического сопоставления. */
    public static final PermissionId COLOR_SETTING_EDIT =
            PermissionId.of("reference.color-setting.edit");

    private static final List<PermissionId> ALL =
            List.of(COLOR_VIEW, COLOR_EDIT, COLOR_MAPPING_EDIT, COLOR_SETTING_EDIT);

    private ReferenceDataPermissions() {}

    /** All four Reference Data permissions in declaration order. */
    public static List<PermissionId> all() {
        return ALL;
    }

    public static Set<PermissionId> asSet() {
        return new LinkedHashSet<>(ALL);
    }
}
