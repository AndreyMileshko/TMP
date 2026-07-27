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
    public static final String ORDER_LIST_FXML = "com/tmp/ui/shell/screen/orderlist/OrderListScreen.fxml";
    public static final String ORDER_EDITOR_FXML = "com/tmp/ui/shell/screen/ordereditor/OrderEditorScreen.fxml";
    public static final String ORDER_ITEM_LIST_FXML =
            "com/tmp/ui/shell/screen/orderitemlist/OrderItemListScreen.fxml";
    public static final String ORDER_ITEM_EDITOR_FXML =
            "com/tmp/ui/shell/screen/orderitemeditor/OrderItemEditorScreen.fxml";
    public static final String ORDER_ITEM_SPECIFICATION_EDITOR_FXML =
            "com/tmp/ui/shell/screen/orderspecificationeditor/OrderItemSpecificationEditorScreen.fxml";

    public static final String MAIN_SCREEN_ID = "main";
    public static final String ACCESS_DENIED_SCREEN_ID = "access-denied";
    public static final String USER_ADMIN_SCREEN_ID = "security.view.users";
    public static final String ROLE_ADMIN_SCREEN_ID = "security.view.roles";
    public static final String AUDIT_SCREEN_ID = "security.view.audit";
    /** Must match Order Management Capability {@code viewId} ({@code order.view.orders}). */
    public static final String ORDER_LIST_SCREEN_ID = "order.view.orders";
    public static final String ORDER_LIST_NAVIGATION_ID = "order.nav.orders";
    public static final String ORDER_LIST_REQUIRED_PERMISSION = "order.order.view";
    public static final String ORDER_EDITOR_SCREEN_ID = "order.view.order-editor";
    public static final String ORDER_ITEM_LIST_SCREEN_ID = "order.view.order-items";
    public static final String ORDER_ITEM_EDITOR_SCREEN_ID = "order.view.order-item-editor";
    public static final String ORDER_ITEM_SPECIFICATION_EDITOR_SCREEN_ID =
            "order.view.order-item-specification-editor";
    public static final String ORDER_CREATE_PERMISSION = "order.order.create";
    public static final String ORDER_ITEM_VIEW_PERMISSION = "order.item.view";
    public static final String ORDER_ITEM_CREATE_PERMISSION = "order.item.create";
    public static final String ORDER_ITEM_EDIT_PERMISSION = "order.item.edit";
    public static final String ORDER_ITEM_CANCEL_PERMISSION = "order.item.cancel";
    public static final String ORDER_ITEM_APPROVE_PERMISSION = "order.item.approve";
    public static final String ORDER_REVISION_CREATE_PERMISSION = "order.revision.create";
    public static final String ORDER_REVISION_EDIT_PERMISSION = "order.revision.edit";
    public static final String ORDER_SPECIFICATION_VIEW_PERMISSION = "order.specification.view";

    private UiShellScreens() {
    }
}
