package com.tmp.ui.shell.screen.roleadmin;

/**
 * User-visible Russian messages for the Roles screen (UTF-8 source).
 */
final class RoleAdministrationMessages {

    static final String SELECT_ROLE = "Выберите роль";
    static final String ROLE_UPDATED = "Роль успешно изменена.";
    static final String ROLE_CREATED = "Роль успешно создана.";
    static final String ROLE_DELETED = "Роль успешно удалена.";
    static final String ROLE_ASSIGNED = "Роль успешно назначена пользователю.";
    static final String ROLE_REVOKED = "Роль успешно отозвана у пользователя.";
    static final String OPERATION_FAILED = "Операция не выполнена";

    private RoleAdministrationMessages() {
    }

    static String userNotFound(String login) {
        return "Пользователь не найден: " + login;
    }
}
