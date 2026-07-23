package com.tmp.security.application;

import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import com.tmp.security.domain.UserStatus;
import java.util.List;
import java.util.Objects;

public final class DefaultUserAdministrationService implements UserAdministrationService {

    private final UserAdministrationApplicationService users;
    private final PasswordApplicationService passwords;

    public DefaultUserAdministrationService(
            UserAdministrationApplicationService users, PasswordApplicationService passwords) {
        this.users = Objects.requireNonNull(users, "users");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
    }

    @Override
    public UserSummary createUser(Login login, DisplayName displayName, char[] initialPassword) {
        return SecurityApiMapper.toSummary(users.createUser(login, displayName, initialPassword));
    }

    @Override
    public UserSummary updateUser(UserId userId, DisplayName newDisplayName) {
        return SecurityApiMapper.toSummary(users.updateUser(userId, newDisplayName));
    }

    @Override
    public UserSummary deleteUser(UserId userId) {
        return SecurityApiMapper.toSummary(users.deleteUser(userId));
    }

    @Override
    public List<UserSummary> listUsers(int pageIndex, int pageSize, String statusFilter) {
        UserStatus status = statusFilter == null || statusFilter.isBlank()
                ? null
                : UserStatus.valueOf(statusFilter);
        return users.listUsers(pageIndex, pageSize, status).stream()
                .map(SecurityApiMapper::toSummary)
                .toList();
    }

    @Override
    public void changeOwnPassword(char[] currentPassword, char[] newPassword) {
        passwords.changeOwnPassword(currentPassword, newPassword);
    }

    @Override
    public void resetPassword(UserId targetUserId, char[] newPassword) {
        passwords.resetPassword(targetUserId, newPassword);
    }
}
