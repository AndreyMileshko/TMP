package com.tmp.ui.shell;

/**
 * Screen identifiers and FXML resource paths for the UI shell (Spring-free).
 */
public final class UiShellScreens {

    public static final String LOGIN_FXML = "com/tmp/ui/shell/screen/login/LoginScreen.fxml";
    public static final String MAIN_FXML = "com/tmp/ui/shell/screen/main/MainWindow.fxml";
    public static final String ACCESS_DENIED_FXML = "com/tmp/ui/shell/screen/accessdenied/AccessDeniedScreen.fxml";
    public static final String USER_ADMIN_FXML = "com/tmp/ui/shell/screen/useradmin/UserAdministrationScreen.fxml";
    public static final String ROLE_ADMIN_FXML = "com/tmp/ui/shell/screen/roleadmin/RoleAdministrationScreen.fxml";
    public static final String AUDIT_FXML = "com/tmp/ui/shell/screen/audit/SecurityAuditScreen.fxml";

    public static final String MAIN_SCREEN_ID = "main";
    public static final String ACCESS_DENIED_SCREEN_ID = "access-denied";
    public static final String USER_ADMIN_SCREEN_ID = "security.view.users";
    public static final String ROLE_ADMIN_SCREEN_ID = "security.view.roles";
    public static final String AUDIT_SCREEN_ID = "security.view.audit";

    private UiShellScreens() {
    }
}
