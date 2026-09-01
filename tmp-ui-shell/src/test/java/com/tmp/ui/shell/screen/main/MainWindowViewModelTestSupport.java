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
import java.util.concurrent.atomic.AtomicReference;

final class MainWindowViewModelTestSupport {

    private MainWindowViewModelTestSupport() {
    }

    static final class EmptyCatalogue implements ShellNavigationCatalogue {
        @Override
        public List<ShellNavEntry> entries() {
            return List.of();
        }
    }

    static final class SingleEntryCatalogue implements ShellNavigationCatalogue {
        private final ShellNavEntry entry;

        SingleEntryCatalogue(ShellNavEntry entry) {
            this.entry = entry;
        }

        @Override
        public List<ShellNavEntry> entries() {
            return List.of(entry);
        }
    }

    static final class MultiEntryCatalogue implements ShellNavigationCatalogue {
        private final List<ShellNavEntry> entries;

        MultiEntryCatalogue(List<ShellNavEntry> entries) {
            this.entries = List.copyOf(entries);
        }

        @Override
        public List<ShellNavEntry> entries() {
            return entries;
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
        public SessionSummary completePasswordSetup(
                Login login, String activationCode, char[] newPassword, char[] confirmPassword) {
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

    static final class SessionAuthn implements AuthenticationService {
        private final SessionSummary session;

        SessionAuthn(SessionSummary session) {
            this.session = session;
        }

        @Override
        public SessionSummary login(Login login, char[] password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SessionSummary completePasswordSetup(
                Login login, String activationCode, char[] newPassword, char[] confirmPassword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void logout() {
        }

        @Override
        public Optional<SessionSummary> currentSession() {
            return Optional.of(session);
        }

        @Override
        public boolean isAuthenticated() {
            return true;
        }
    }

    static final class StatefulSessionAuthn implements AuthenticationService {
        private final AtomicReference<SessionSummary> session = new AtomicReference<>();

        void setSession(SessionSummary value) {
            session.set(value);
        }

        void clearSession() {
            session.set(null);
        }

        @Override
        public SessionSummary login(Login login, char[] password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SessionSummary completePasswordSetup(
                Login login, String activationCode, char[] newPassword, char[] confirmPassword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void logout() {
            clearSession();
        }

        @Override
        public Optional<SessionSummary> currentSession() {
            return Optional.ofNullable(session.get());
        }

        @Override
        public boolean isAuthenticated() {
            return session.get() != null;
        }
    }
}
