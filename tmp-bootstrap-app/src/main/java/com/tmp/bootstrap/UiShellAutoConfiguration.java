package com.tmp.bootstrap;

import com.tmp.capability.api.CapabilityEngine;
import com.tmp.security.api.AuditQueryService;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.ui.shell.SceneNavigator;
import com.tmp.ui.shell.UiShellEntryPoint;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.navigation.NavigationService;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.navigation.ShellNavigationCatalogue;
import com.tmp.ui.shell.screen.accessdenied.AccessDeniedViewModel;
import com.tmp.ui.shell.screen.audit.SecurityAuditViewModel;
import com.tmp.ui.shell.screen.login.LoginViewModel;
import com.tmp.ui.shell.screen.main.MainWindowViewModel;
import com.tmp.ui.shell.screen.roleadmin.RoleAdministrationViewModel;
import com.tmp.ui.shell.screen.useradmin.UserAdministrationViewModel;
import jakarta.annotation.PostConstruct;
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
    UiShellScreenRegistrar uiShellScreenRegistrar(
            NavigationService navigationService,
            LoginViewModel loginViewModel,
            MainWindowViewModel mainWindowViewModel,
            AccessDeniedViewModel accessDeniedViewModel,
            UserAdministrationViewModel userAdministrationViewModel,
            RoleAdministrationViewModel roleAdministrationViewModel,
            SecurityAuditViewModel securityAuditViewModel) {
        return new UiShellScreenRegistrar(
                navigationService,
                loginViewModel,
                mainWindowViewModel,
                accessDeniedViewModel,
                userAdministrationViewModel,
                roleAdministrationViewModel,
                securityAuditViewModel);
    }

    @Bean
    UiShellEntryPoint uiShellEntryPoint(NavigationService navigationService, SceneNavigator sceneNavigator) {
        return new UiShellEntryPoint(
                navigationService, UiShellEntryPoint.LOGIN_SCREEN_ID, sceneNavigator);
    }

    static final class UiShellScreenRegistrar {

        private final NavigationService navigationService;
        private final LoginViewModel loginViewModel;
        private final MainWindowViewModel mainWindowViewModel;
        private final AccessDeniedViewModel accessDeniedViewModel;
        private final UserAdministrationViewModel userAdministrationViewModel;
        private final RoleAdministrationViewModel roleAdministrationViewModel;
        private final SecurityAuditViewModel securityAuditViewModel;

        UiShellScreenRegistrar(
                NavigationService navigationService,
                LoginViewModel loginViewModel,
                MainWindowViewModel mainWindowViewModel,
                AccessDeniedViewModel accessDeniedViewModel,
                UserAdministrationViewModel userAdministrationViewModel,
                RoleAdministrationViewModel roleAdministrationViewModel,
                SecurityAuditViewModel securityAuditViewModel) {
            this.navigationService = navigationService;
            this.loginViewModel = loginViewModel;
            this.mainWindowViewModel = mainWindowViewModel;
            this.accessDeniedViewModel = accessDeniedViewModel;
            this.userAdministrationViewModel = userAdministrationViewModel;
            this.roleAdministrationViewModel = roleAdministrationViewModel;
            this.securityAuditViewModel = securityAuditViewModel;
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
        }
    }
}
