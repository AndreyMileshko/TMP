package com.tmp.reference.security;

import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.security.api.PermissionId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Immutable set of Reference Data {@link PermissionDescriptor}s (TMP-SPEC-015 §15).
 *
 * <p>Duplicates are rejected at class initialization. Descriptors are metadata only — they do not
 * grant permissions to users or roles.
 */
public final class ReferenceDataPermissionCatalog {

    private static final List<PermissionDescriptor> DESCRIPTORS =
            List.of(
                    descriptor(
                            ReferenceDataPermissions.COLOR_VIEW,
                            "Просмотр справочника цветов",
                            "Просмотр каталога цветов TMP, соответствий СО7 и состояния"
                                    + " переключателя; доступ к пункту «Справочники»"),
                    descriptor(
                            ReferenceDataPermissions.COLOR_EDIT,
                            "Изменение цветов TMP",
                            "Создание, архивирование и восстановление цветов TMP"),
                    descriptor(
                            ReferenceDataPermissions.COLOR_MAPPING_EDIT,
                            "Изменение соответствий СО7",
                            "Управление глобальными соответствиями СО7, включая сохранение из"
                                    + " окна импорта"),
                    descriptor(
                            ReferenceDataPermissions.COLOR_SETTING_EDIT,
                            "Изменение настройки сопоставления",
                            "Изменение общесистемного переключателя автоматического"
                                    + " сопоставления"));

    private static final Map<String, PermissionDescriptor> BY_ID =
            DESCRIPTORS.stream()
                    .collect(
                            Collectors.toMap(
                                    PermissionDescriptor::permissionId,
                                    descriptor -> descriptor,
                                    (left, right) -> {
                                        throw new IllegalStateException(
                                                "Duplicate permission descriptor: "
                                                        + left.permissionId());
                                    },
                                    LinkedHashMap::new));

    static {
        if (DESCRIPTORS.size() != ReferenceDataPermissions.all().size()) {
            throw new IllegalStateException(
                    "Permission descriptor count must equal ReferenceDataPermissions.all()");
        }
        if (BY_ID.size() != DESCRIPTORS.size()) {
            throw new IllegalStateException("Duplicate permission descriptors detected");
        }
    }

    private ReferenceDataPermissionCatalog() {}

    public static List<PermissionDescriptor> all() {
        return DESCRIPTORS;
    }

    public static Optional<PermissionDescriptor> findById(PermissionId permissionId) {
        Objects.requireNonNull(permissionId, "permissionId");
        return Optional.ofNullable(BY_ID.get(permissionId.value()));
    }

    public static boolean contains(PermissionId permissionId) {
        Objects.requireNonNull(permissionId, "permissionId");
        return BY_ID.containsKey(permissionId.value());
    }

    public static boolean containsCode(String permissionCode) {
        Objects.requireNonNull(permissionCode, "permissionCode");
        return BY_ID.containsKey(permissionCode);
    }

    private static PermissionDescriptor descriptor(
            PermissionId permissionId, String displayName, String description) {
        return PermissionDescriptor.of(permissionId.value(), displayName, description);
    }
}
