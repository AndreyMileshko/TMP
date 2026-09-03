package com.tmp.bootstrap;

import com.tmp.capability.api.CapabilityEngine;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.OrderWorklistQuery;
import com.tmp.order.api.imports.OrderImportService;
import com.tmp.order.api.imports.StxtOrderFileParser;
import com.tmp.order.api.ui.CurrentOrderItemSpecificationUiService;
import com.tmp.order.api.ui.OrderDocumentUiService;
import com.tmp.order.api.ui.OrderItemDocumentUiService;
import com.tmp.order.api.ui.OrderItemEditorQueryService;
import com.tmp.order.api.ui.OrderItemSpecificationEditorQueryService;
import com.tmp.security.api.AuditQueryService;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserUiPreferenceService;
import com.tmp.ui.shell.SceneNavigator;
import com.tmp.ui.shell.UiShellEntryPoint;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.navigation.NavigationService;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.navigation.ShellHistoryEntry;
import com.tmp.ui.shell.navigation.ShellNavigationCatalogue;
import com.tmp.ui.shell.order.worklist.OrderListMemento;
import com.tmp.ui.shell.order.worklist.OrderOperationalListService;
import com.tmp.ui.shell.screen.accessdenied.AccessDeniedViewModel;
import com.tmp.ui.shell.screen.audit.SecurityAuditViewModel;
import com.tmp.ui.shell.screen.login.LoginViewModel;
import com.tmp.ui.shell.screen.main.MainWindowViewModel;
import com.tmp.ui.shell.screen.ordereditor.OrderEditorViewModel;
import com.tmp.ui.shell.screen.orderimport.OrderImportViewModel;
import com.tmp.ui.shell.screen.orderitemeditor.OrderItemEditorViewModel;
import com.tmp.ui.shell.screen.orderitemlist.OrderItemListMemento;
import com.tmp.ui.shell.screen.orderitemlist.OrderItemListViewModel;
import com.tmp.ui.shell.screen.orderlist.OrderListViewModel;
import com.tmp.ui.shell.screen.orderspecificationeditor.OrderItemSpecificationEditorViewModel;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchViewModel;
import com.tmp.ui.shell.screen.roleadmin.RoleAdministrationViewModel;
import com.tmp.ui.shell.screen.useradmin.UserAdministrationViewModel;
import com.tmp.ui.shell.screen.warehouse.WarehouseWorkbenchViewModel;
import com.tmp.production.api.ProductionApplicationApi;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.warehouse.api.WarehouseApi;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import javafx.application.Platform;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for UI shell screens. Lives in bootstrap so {@code com.tmp.ui..} stays Spring-free
 * (Stage 0 architecture rule).
 */
@Configuration
public class UiShellAutoConfiguration {

    @Bean
    ShellNavigationCatalogue shellNavigationCatalogue(CapabilityEngine capabilityEngine) {
        return new CapabilityShellNavigationCatalogue(capabilityEngine);
    }

    @Bean
    NavigationService navigationService() {
        return NavigationServices.createDefault();
    }

    @Bean
    SceneNavigator sceneNavigator(NavigationService navigationService) {
        return new SceneNavigator(navigationService);
    }

    @Bean
    LoginViewModel loginViewModel(AuthenticationService authenticationService, SceneNavigator sceneNavigator) {
        LoginViewModel viewModel = new LoginViewModel(authenticationService);
        viewModel.setOnLoginSuccess(
                () -> Platform.runLater(() -> sceneNavigator.show(UiShellScreens.MAIN_SCREEN_ID)));
        return viewModel;
    }

    @Bean
    MainWindowViewModel mainWindowViewModel(
            ShellNavigationCatalogue shellNavigationCatalogue,
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            NavigationService navigationService,
            SceneNavigator sceneNavigator,
            AccessDeniedViewModel accessDeniedViewModel) {
        MainWindowViewModel viewModel = new MainWindowViewModel(
                shellNavigationCatalogue, authorizationService, authenticationService, navigationService);
        viewModel.setAfterLogout(
                () -> Platform.runLater(() -> sceneNavigator.show(UiShellEntryPoint.LOGIN_SCREEN_ID)));
        viewModel.setOnAccessDenied(message -> Platform.runLater(() -> {
            accessDeniedViewModel.setMessage(message);
            sceneNavigator.show(UiShellScreens.ACCESS_DENIED_SCREEN_ID);
        }));
        return viewModel;
    }

    @Bean
    AccessDeniedViewModel accessDeniedViewModel(SceneNavigator sceneNavigator) {
        AccessDeniedViewModel viewModel = new AccessDeniedViewModel();
        viewModel.setOnBack(() -> Platform.runLater(() -> sceneNavigator.show(UiShellScreens.MAIN_SCREEN_ID)));
        return viewModel;
    }

    @Bean
    UserAdministrationViewModel userAdministrationViewModel(
            UserAdministrationService userAdministrationService, AuthorizationService authorizationService) {
        return new UserAdministrationViewModel(userAdministrationService, authorizationService);
    }

    @Bean
    RoleAdministrationViewModel roleAdministrationViewModel(
            RoleAdministrationService roleAdministrationService,
            UserAdministrationService userAdministrationService,
            AuthorizationService authorizationService) {
        return new RoleAdministrationViewModel(
                roleAdministrationService, userAdministrationService, authorizationService);
    }

    @Bean
    SecurityAuditViewModel securityAuditViewModel(AuditQueryService auditQueryService) {
        return new SecurityAuditViewModel(auditQueryService);
    }

    @Bean
    OrderOperationalListService orderOperationalListService(
            OrderWorklistQuery orderWorklistQuery, ProductionQueryApi productionQueryApi) {
        return new OrderOperationalListService(orderWorklistQuery, productionQueryApi);
    }

    @Bean
    OrderListViewModel orderListViewModel(
            OrderOperationalListService orderOperationalListService,
            OrderWorklistQuery orderWorklistQuery,
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            UserUiPreferenceService userUiPreferenceService,
            Clock clock) {
        return new OrderListViewModel(
                orderOperationalListService,
                orderWorklistQuery,
                authorizationService,
                authenticationService,
                userUiPreferenceService,
                clock);
    }

    @Bean
    OrderImportViewModel orderImportViewModel(
            OrderImportService orderImportService,
            StxtOrderFileParser stxtOrderFileParser,
            AuthorizationService authorizationService) {
        return new OrderImportViewModel(orderImportService, stxtOrderFileParser, authorizationService);
    }

    @Bean
    OrderEditorViewModel orderEditorViewModel(
            OrderQueryService orderQueryService,
            OrderDocumentUiService orderDocumentUiService,
            AuthorizationService authorizationService,
            ProductionQueryApi productionQueryApi) {
        return new OrderEditorViewModel(
                orderQueryService, orderDocumentUiService, authorizationService, productionQueryApi);
    }

    @Bean
    OrderItemListViewModel orderItemListViewModel(
            OrderQueryService orderQueryService,
            AuthorizationService authorizationService,
            OrderItemEditorQueryService orderItemEditorQueryService,
            ProductionQueryApi productionQueryApi,
            OrderItemDocumentUiService orderItemDocumentUiService) {
        return new OrderItemListViewModel(
                orderQueryService,
                authorizationService,
                orderItemEditorQueryService,
                productionQueryApi,
                orderItemDocumentUiService);
    }

    @Bean
    OrderItemEditorViewModel orderItemEditorViewModel(
            OrderItemDocumentUiService orderItemDocumentUiService,
            OrderItemEditorQueryService orderItemEditorQueryService,
            OrderQueryService orderQueryService,
            AuthorizationService authorizationService,
            CurrentOrderItemSpecificationUiService currentOrderItemSpecificationUiService,
            ProductionQueryApi productionQueryApi) {
        return new OrderItemEditorViewModel(
                orderItemDocumentUiService,
                orderItemEditorQueryService,
                authorizationService,
                orderQueryService,
                currentOrderItemSpecificationUiService,
                productionQueryApi);
    }

    @Bean
    OrderItemSpecificationEditorViewModel orderItemSpecificationEditorViewModel(
            OrderItemDocumentUiService orderItemDocumentUiService,
            OrderItemSpecificationEditorQueryService orderItemSpecificationEditorQueryService,
            AuthorizationService authorizationService,
            OrderItemEditorQueryService orderItemEditorQueryService,
            OrderQueryService orderQueryService,
            CurrentOrderItemSpecificationUiService currentOrderItemSpecificationUiService,
            ProductionQueryApi productionQueryApi) {
        return new OrderItemSpecificationEditorViewModel(
                orderItemDocumentUiService,
                orderItemSpecificationEditorQueryService,
                authorizationService,
                orderItemEditorQueryService,
                orderQueryService,
                currentOrderItemSpecificationUiService,
                productionQueryApi);
    }

    @Bean
    WarehouseWorkbenchViewModel warehouseWorkbenchViewModel(
            WarehouseApi warehouseApi, AuthorizationService authorizationService) {
        return new WarehouseWorkbenchViewModel(warehouseApi, authorizationService);
    }

    @Bean
    ProductionWorkbenchViewModel productionWorkbenchViewModel(
            ProductionQueryApi productionQueryApi,
            ProductionApplicationApi productionApplicationApi,
            OrderQueryService orderQueryService,
            WarehouseApi warehouseApi,
            AuthorizationService authorizationService,
            AuthenticationService authenticationService) {
        return new ProductionWorkbenchViewModel(
                productionQueryApi,
                productionApplicationApi,
                orderQueryService,
                warehouseApi,
                authorizationService,
                authenticationService);
    }

    @Bean
    OrderScreenNavigationBridge orderScreenNavigationBridge(
            OrderListViewModel orderListViewModel,
            OrderEditorViewModel orderEditorViewModel,
            OrderImportViewModel orderImportViewModel,
            OrderItemListViewModel orderItemListViewModel,
            OrderItemEditorViewModel orderItemEditorViewModel,
            OrderItemSpecificationEditorViewModel orderItemSpecificationEditorViewModel,
            MainWindowViewModel mainWindowViewModel) {
        return new OrderScreenNavigationBridge(
                orderListViewModel,
                orderEditorViewModel,
                orderImportViewModel,
                orderItemListViewModel,
                orderItemEditorViewModel,
                orderItemSpecificationEditorViewModel,
                mainWindowViewModel);
    }

    @Bean
    UiShellScreenRegistrar uiShellScreenRegistrar(
            NavigationService navigationService,
            LoginViewModel loginViewModel,
            MainWindowViewModel mainWindowViewModel,
            AccessDeniedViewModel accessDeniedViewModel,
            UserAdministrationViewModel userAdministrationViewModel,
            RoleAdministrationViewModel roleAdministrationViewModel,
            SecurityAuditViewModel securityAuditViewModel,
            OrderListViewModel orderListViewModel,
            OrderEditorViewModel orderEditorViewModel,
            OrderImportViewModel orderImportViewModel,
            OrderItemListViewModel orderItemListViewModel,
            OrderItemEditorViewModel orderItemEditorViewModel,
            OrderItemSpecificationEditorViewModel orderItemSpecificationEditorViewModel,
            WarehouseWorkbenchViewModel warehouseWorkbenchViewModel,
            ProductionWorkbenchViewModel productionWorkbenchViewModel) {
        return new UiShellScreenRegistrar(
                navigationService,
                loginViewModel,
                mainWindowViewModel,
                accessDeniedViewModel,
                userAdministrationViewModel,
                roleAdministrationViewModel,
                securityAuditViewModel,
                orderListViewModel,
                orderEditorViewModel,
                orderImportViewModel,
                orderItemListViewModel,
                orderItemEditorViewModel,
                orderItemSpecificationEditorViewModel,
                warehouseWorkbenchViewModel,
                productionWorkbenchViewModel);
    }

    @Bean
    UiShellEntryPoint uiShellEntryPoint(NavigationService navigationService, SceneNavigator sceneNavigator) {
        return new UiShellEntryPoint(
                navigationService, UiShellEntryPoint.LOGIN_SCREEN_ID, sceneNavigator);
    }

    static final class OrderScreenNavigationBridge {

        private final OrderListViewModel orderListViewModel;
        private final OrderEditorViewModel orderEditorViewModel;
        private final OrderImportViewModel orderImportViewModel;
        private final OrderItemListViewModel orderItemListViewModel;
        private final OrderItemEditorViewModel orderItemEditorViewModel;
        private final OrderItemSpecificationEditorViewModel orderItemSpecificationEditorViewModel;
        private final MainWindowViewModel mainWindowViewModel;

        OrderScreenNavigationBridge(
                OrderListViewModel orderListViewModel,
                OrderEditorViewModel orderEditorViewModel,
                OrderImportViewModel orderImportViewModel,
                OrderItemListViewModel orderItemListViewModel,
                OrderItemEditorViewModel orderItemEditorViewModel,
                OrderItemSpecificationEditorViewModel orderItemSpecificationEditorViewModel,
                MainWindowViewModel mainWindowViewModel) {
            this.orderListViewModel = orderListViewModel;
            this.orderEditorViewModel = orderEditorViewModel;
            this.orderImportViewModel = orderImportViewModel;
            this.orderItemListViewModel = orderItemListViewModel;
            this.orderItemEditorViewModel = orderItemEditorViewModel;
            this.orderItemSpecificationEditorViewModel = orderItemSpecificationEditorViewModel;
            this.mainWindowViewModel = mainWindowViewModel;
        }

        @PostConstruct
        void wireCallbacks() {
            mainWindowViewModel.setOnSidebarScreen(screenId -> {
                if (UiShellScreens.ORDER_LIST_SCREEN_ID.equals(screenId)) {
                    orderListViewModel.resetForSidebarOpen();
                }
            });
            orderListViewModel.setOnCreateOrder(() -> Platform.runLater(() -> {
                rememberList();
                mainWindowViewModel.navigate(createEditorEntry());
            }));
            orderListViewModel.setOnImportOrder(() -> Platform.runLater(() -> {
                rememberList();
                mainWindowViewModel.navigate(importEntry());
            }));
            orderListViewModel.setOnOpenOrder((OrderId orderId) -> Platform.runLater(() -> {
                rememberList();
                mainWindowViewModel.navigate(editorEntry(orderId));
            }));
            orderImportViewModel.setOnCancel(() -> Platform.runLater(() -> {
                if (mainWindowViewModel.canGoBackProperty().get()) {
                    mainWindowViewModel.goBack();
                    return;
                }
                orderListViewModel.refresh();
                mainWindowViewModel.navigate(
                        ShellHistoryEntry.of(
                                UiShellScreens.ORDER_LIST_SCREEN_ID,
                                UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION,
                                orderListViewModel::refresh));
            }));
            orderImportViewModel.setOnImportSuccess(() -> {
                // Stay on import screen to show result; list refresh on cancel/back.
            });
            orderEditorViewModel.setOnOpenItems(() -> Platform.runLater(() -> {
                OrderId orderId = orderEditorViewModel.currentOrderId();
                OrderStatus status = orderEditorViewModel.currentOrderStatus();
                if (orderId == null || status == null) {
                    return;
                }
                mainWindowViewModel.replaceCurrent(editorEntry(orderId));
                mainWindowViewModel.navigate(itemListEntry(orderId));
            }));
            orderEditorViewModel.setOnOrderCreated(orderId -> Platform.runLater(() ->
                    mainWindowViewModel.replaceCurrent(editorEntry(orderId))));
            orderItemListViewModel.setOnCreateItem(() -> Platform.runLater(() -> {
                OrderId orderId = orderItemListViewModel.currentOrderId();
                if (orderId == null) {
                    return;
                }
                mainWindowViewModel.replaceCurrent(itemListEntry(orderId));
                mainWindowViewModel.navigate(itemCreateEntry(orderId));
            }));
            orderItemListViewModel.setOnOpenSpecification((OrderItemId itemId) -> Platform.runLater(() -> {
                OrderId orderId = orderItemListViewModel.currentOrderId();
                if (orderId != null) {
                    mainWindowViewModel.replaceCurrent(itemListEntry(orderId));
                }
                mainWindowViewModel.navigate(specificationEntry(itemId));
            }));
            orderItemListViewModel.setOnEditItem((OrderItemId itemId) -> Platform.runLater(() -> {
                OrderId orderId = orderItemListViewModel.currentOrderId();
                if (orderId != null) {
                    mainWindowViewModel.replaceCurrent(itemListEntry(orderId));
                }
                mainWindowViewModel.navigate(editItemEntry(itemId));
            }));
            orderImportViewModel.setOnOpenImportedOrder(orderId -> Platform.runLater(() -> {
                orderListViewModel.refresh();
                mainWindowViewModel.navigate(editorEntry(orderId));
            }));
            orderImportViewModel.setOnGoToOrderList(() -> Platform.runLater(() -> {
                orderListViewModel.refresh();
                if (mainWindowViewModel.canGoBackProperty().get()) {
                    mainWindowViewModel.goBack();
                    return;
                }
                mainWindowViewModel.navigate(
                        ShellHistoryEntry.of(
                                UiShellScreens.ORDER_LIST_SCREEN_ID,
                                UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION,
                                orderListViewModel::refresh));
            }));
            orderItemEditorViewModel.setOnOpenSpecification(target -> Platform.runLater(() -> {
                OrderItemId itemId = target.orderItemId();
                mainWindowViewModel.replaceCurrent(itemEditorEntry(itemId));
                mainWindowViewModel.navigate(specificationEntry(itemId));
            }));
        }

        private void rememberList() {
            OrderListMemento memento = orderListViewModel.captureMemento();
            mainWindowViewModel.replaceCurrent(
                    ShellHistoryEntry.of(
                            UiShellScreens.ORDER_LIST_SCREEN_ID,
                            UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION,
                            () -> orderListViewModel.restoreMemento(memento)));
        }

        private ShellHistoryEntry createEditorEntry() {
            return ShellHistoryEntry.of(
                    UiShellScreens.ORDER_EDITOR_SCREEN_ID,
                    UiShellScreens.ORDER_CREATE_PERMISSION,
                    orderEditorViewModel::openCreate);
        }

        private ShellHistoryEntry editorEntry(OrderId orderId) {
            return ShellHistoryEntry.of(
                    UiShellScreens.ORDER_EDITOR_SCREEN_ID,
                    UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION,
                    () -> orderEditorViewModel.openExisting(orderId));
        }

        private ShellHistoryEntry importEntry() {
            return ShellHistoryEntry.of(
                    UiShellScreens.ORDER_IMPORT_SCREEN_ID,
                    UiShellScreens.ORDER_CREATE_PERMISSION,
                    orderImportViewModel::open);
        }

        private ShellHistoryEntry itemListEntry(OrderId orderId) {
            OrderItemListMemento memento = orderItemListViewModel.captureMemento();
            boolean restoreMemento =
                    memento != null && orderId.equals(memento.orderId());
            OrderItemListMemento restore = restoreMemento ? memento : null;
            return ShellHistoryEntry.of(
                    UiShellScreens.ORDER_ITEM_LIST_SCREEN_ID,
                    UiShellScreens.ORDER_ITEM_VIEW_PERMISSION,
                    () -> {
                        orderEditorViewModel.openExisting(orderId);
                        OrderStatus status = orderEditorViewModel.currentOrderStatus();
                        if (status == null) {
                            return;
                        }
                        if (restore != null) {
                            orderItemListViewModel.restoreMemento(restore, status);
                        } else {
                            orderItemListViewModel.openForOrder(orderId, status);
                        }
                    });
        }

        private ShellHistoryEntry itemCreateEntry(OrderId orderId) {
            return ShellHistoryEntry.of(
                    UiShellScreens.ORDER_ITEM_EDITOR_SCREEN_ID,
                    UiShellScreens.ORDER_ITEM_VIEW_PERMISSION,
                    () -> orderItemEditorViewModel.openCreate(orderId));
        }

        private ShellHistoryEntry itemEditorEntry(OrderItemId itemId) {
            return ShellHistoryEntry.of(
                    UiShellScreens.ORDER_ITEM_EDITOR_SCREEN_ID,
                    UiShellScreens.ORDER_ITEM_VIEW_PERMISSION,
                    () -> orderItemEditorViewModel.openExisting(itemId));
        }

        private ShellHistoryEntry editItemEntry(OrderItemId itemId) {
            return ShellHistoryEntry.of(
                    UiShellScreens.ORDER_ITEM_EDITOR_SCREEN_ID,
                    UiShellScreens.ORDER_ITEM_EDIT_PERMISSION,
                    () -> orderItemEditorViewModel.openExisting(itemId));
        }

        private ShellHistoryEntry specificationEntry(OrderItemId itemId) {
            return ShellHistoryEntry.of(
                    UiShellScreens.ORDER_ITEM_SPECIFICATION_EDITOR_SCREEN_ID,
                    UiShellScreens.ORDER_SPECIFICATION_VIEW_PERMISSION,
                    () -> orderItemSpecificationEditorViewModel.openCurrent(itemId));
        }
    }

    static final class UiShellScreenRegistrar {

        private final NavigationService navigationService;
        private final LoginViewModel loginViewModel;
        private final MainWindowViewModel mainWindowViewModel;
        private final AccessDeniedViewModel accessDeniedViewModel;
        private final UserAdministrationViewModel userAdministrationViewModel;
        private final RoleAdministrationViewModel roleAdministrationViewModel;
        private final SecurityAuditViewModel securityAuditViewModel;
        private final OrderListViewModel orderListViewModel;
        private final OrderEditorViewModel orderEditorViewModel;
        private final OrderImportViewModel orderImportViewModel;
        private final OrderItemListViewModel orderItemListViewModel;
        private final OrderItemEditorViewModel orderItemEditorViewModel;
        private final OrderItemSpecificationEditorViewModel orderItemSpecificationEditorViewModel;
        private final WarehouseWorkbenchViewModel warehouseWorkbenchViewModel;
        private final ProductionWorkbenchViewModel productionWorkbenchViewModel;

        UiShellScreenRegistrar(
                NavigationService navigationService,
                LoginViewModel loginViewModel,
                MainWindowViewModel mainWindowViewModel,
                AccessDeniedViewModel accessDeniedViewModel,
                UserAdministrationViewModel userAdministrationViewModel,
                RoleAdministrationViewModel roleAdministrationViewModel,
                SecurityAuditViewModel securityAuditViewModel,
                OrderListViewModel orderListViewModel,
                OrderEditorViewModel orderEditorViewModel,
                OrderImportViewModel orderImportViewModel,
                OrderItemListViewModel orderItemListViewModel,
                OrderItemEditorViewModel orderItemEditorViewModel,
                OrderItemSpecificationEditorViewModel orderItemSpecificationEditorViewModel,
                WarehouseWorkbenchViewModel warehouseWorkbenchViewModel,
                ProductionWorkbenchViewModel productionWorkbenchViewModel) {
            this.navigationService = navigationService;
            this.loginViewModel = loginViewModel;
            this.mainWindowViewModel = mainWindowViewModel;
            this.accessDeniedViewModel = accessDeniedViewModel;
            this.userAdministrationViewModel = userAdministrationViewModel;
            this.roleAdministrationViewModel = roleAdministrationViewModel;
            this.securityAuditViewModel = securityAuditViewModel;
            this.orderListViewModel = orderListViewModel;
            this.orderEditorViewModel = orderEditorViewModel;
            this.orderImportViewModel = orderImportViewModel;
            this.orderItemListViewModel = orderItemListViewModel;
            this.orderItemEditorViewModel = orderItemEditorViewModel;
            this.orderItemSpecificationEditorViewModel = orderItemSpecificationEditorViewModel;
            this.warehouseWorkbenchViewModel = warehouseWorkbenchViewModel;
            this.productionWorkbenchViewModel = productionWorkbenchViewModel;
        }

        @PostConstruct
        void registerScreens() {
            navigationService.register(new ScreenRegistration(
                    UiShellEntryPoint.LOGIN_SCREEN_ID, UiShellScreens.LOGIN_FXML, () -> loginViewModel));
            navigationService.register(new ScreenRegistration(
                    UiShellScreens.MAIN_SCREEN_ID, UiShellScreens.MAIN_FXML, () -> mainWindowViewModel));
            navigationService.register(new ScreenRegistration(
                    UiShellScreens.ACCESS_DENIED_SCREEN_ID,
                    UiShellScreens.ACCESS_DENIED_FXML,
                    () -> accessDeniedViewModel));
            navigationService.register(new ScreenRegistration(
                    UiShellScreens.USER_ADMIN_SCREEN_ID,
                    UiShellScreens.USER_ADMIN_FXML,
                    () -> userAdministrationViewModel));
            navigationService.register(new ScreenRegistration(
                    UiShellScreens.ROLE_ADMIN_SCREEN_ID,
                    UiShellScreens.ROLE_ADMIN_FXML,
                    () -> roleAdministrationViewModel));
            navigationService.register(new ScreenRegistration(
                    UiShellScreens.AUDIT_SCREEN_ID, UiShellScreens.AUDIT_FXML, () -> securityAuditViewModel));
            navigationService.register(new ScreenRegistration(
                    UiShellScreens.ORDER_LIST_SCREEN_ID,
                    UiShellScreens.ORDER_LIST_FXML,
                    () -> orderListViewModel));
            navigationService.register(new ScreenRegistration(
                    UiShellScreens.ORDER_EDITOR_SCREEN_ID,
                    UiShellScreens.ORDER_EDITOR_FXML,
                    () -> orderEditorViewModel));
            navigationService.register(new ScreenRegistration(
                    UiShellScreens.ORDER_IMPORT_SCREEN_ID,
                    UiShellScreens.ORDER_IMPORT_FXML,
                    () -> orderImportViewModel));
            navigationService.register(new ScreenRegistration(
                    UiShellScreens.ORDER_ITEM_LIST_SCREEN_ID,
                    UiShellScreens.ORDER_ITEM_LIST_FXML,
                    () -> orderItemListViewModel));
            navigationService.register(new ScreenRegistration(
                    UiShellScreens.ORDER_ITEM_EDITOR_SCREEN_ID,
                    UiShellScreens.ORDER_ITEM_EDITOR_FXML,
                    () -> orderItemEditorViewModel));
            navigationService.register(new ScreenRegistration(
                    UiShellScreens.ORDER_ITEM_SPECIFICATION_EDITOR_SCREEN_ID,
                    UiShellScreens.ORDER_ITEM_SPECIFICATION_EDITOR_FXML,
                    () -> orderItemSpecificationEditorViewModel));
            navigationService.register(new ScreenRegistration(
                    UiShellScreens.WAREHOUSE_WORKBENCH_SCREEN_ID,
                    UiShellScreens.WAREHOUSE_WORKBENCH_FXML,
                    () -> warehouseWorkbenchViewModel));
            navigationService.register(new ScreenRegistration(
                    UiShellScreens.PRODUCTION_WORKBENCH_SCREEN_ID,
                    UiShellScreens.PRODUCTION_WORKBENCH_FXML,
                    () -> productionWorkbenchViewModel));
        }
    }
}
