package com.tmp.ui.shell.screen.main;

import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.ui.shell.navigation.ShellNavEntry;
import com.tmp.ui.shell.navigation.ShellNavigationCatalogue;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

final class MainWindowViewModelTestSupport {

    private MainWindowViewModelTestSupport() {
    }

    static final class EmptyCatalogue implements ShellNavigationCatalogue {
        @Override
        public List<ShellNavEntry> entries() {
            return List.of();
        }
    }

    static final class AllowAllAuthz implements AuthorizationService {
        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return true;
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of();
        }
    }

    static final class RecordingAuthn implements AuthenticationService {
        private final AtomicBoolean loggedOut;

        RecordingAuthn(AtomicBoolean loggedOut) {
            this.loggedOut = loggedOut;
        }

        @Override
        public SessionSummary login(Login login, char[] password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void logout() {
            loggedOut.set(true);
        }

        @Override
        public Optional<SessionSummary> currentSession() {
            return Optional.empty();
        }

        @Override
        public boolean isAuthenticated() {
            return false;
        }
    }
}
