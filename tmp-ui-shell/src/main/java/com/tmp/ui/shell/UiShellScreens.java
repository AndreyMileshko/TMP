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
    public static final String ORDER_IMPORT_FXML =
            "com/tmp/ui/shell/screen/orderimport/OrderImportScreen.fxml";
    public static final String WAREHOUSE_WORKBENCH_FXML =
            "com/tmp/ui/shell/screen/warehouse/WarehouseWorkbenchScreen.fxml";
    public static final String PRODUCTION_WORKBENCH_FXML =
            "com/tmp/ui/shell/screen/production/ProductionWorkbenchScreen.fxml";

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
    public static final String ORDER_IMPORT_SCREEN_ID = "order.view.order-import";
    /** Must match Warehouse Capability {@code viewId} ({@code warehouse.view.workbench}). */
    public static final String WAREHOUSE_WORKBENCH_SCREEN_ID = "warehouse.view.workbench";
    /** Must match Production Capability {@code viewId} ({@code production.view.workbench}). */
    public static final String PRODUCTION_WORKBENCH_SCREEN_ID = "production.view.workbench";
    public static final String PRODUCTION_VIEW_PERMISSION = "production.order.view";
    public static final String PRODUCTION_ACCEPT_PERMISSION = "production.order.accept";
    public static final String PRODUCTION_CHECK_PERMISSION = "production.materials.check";
    public static final String PRODUCTION_TRANSFER_PERMISSION = "production.transfer.create";
    public static final String PRODUCTION_RECEIPT_PERMISSION = "production.receipt.confirm";
    public static final String PRODUCTION_RELEASE_PERMISSION = "production.release.create";
    public static final String PRODUCTION_CANCEL_PERMISSION = "production.cancellation.create";
    public static final String WAREHOUSE_VIEW_PERMISSION = "warehouse.stock.view";
    public static final String WAREHOUSE_RECEIPT_PERMISSION = "warehouse.receipt.create";
    public static final String WAREHOUSE_MOVE_PERMISSION = "warehouse.move.create";
    public static final String WAREHOUSE_TRANSFER_PERMISSION = "warehouse.transfer.create";
    public static final String WAREHOUSE_RESERVATION_PERMISSION = "warehouse.reservation.create";
    public static final String WAREHOUSE_CONSUMPTION_PERMISSION = "warehouse.consumption.create";
    public static final String WAREHOUSE_ADJUSTMENT_PERMISSION = "warehouse.adjustment.create";
    public static final String WAREHOUSE_STRUCTURE_VIEW_PERMISSION = "warehouse.warehouse.view";
    public static final String WAREHOUSE_STRUCTURE_CREATE_PERMISSION = "warehouse.warehouse.create";
    public static final String WAREHOUSE_STRUCTURE_UPDATE_PERMISSION = "warehouse.warehouse.update";
    public static final String WAREHOUSE_STRUCTURE_DELETE_PERMISSION = "warehouse.warehouse.delete";
    public static final String WAREHOUSE_STORAGE_CELL_VIEW_PERMISSION = "warehouse.storage-cell.view";
    public static final String WAREHOUSE_STORAGE_CELL_CREATE_PERMISSION =
            "warehouse.storage-cell.create";
    public static final String WAREHOUSE_STORAGE_CELL_UPDATE_PERMISSION =
            "warehouse.storage-cell.update";
    public static final String WAREHOUSE_STORAGE_CELL_DELETE_PERMISSION =
            "warehouse.storage-cell.delete";
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
