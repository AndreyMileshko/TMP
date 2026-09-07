package com.tmp.security.api;

import java.util.List;

/**
 * Public user administration API.
 */
public interface UserAdministrationService {

    UserCreationResult createUser(Login login, DisplayName displayName);

    UserSummary updateUser(UserId userId, Login login, DisplayName displayName);

    UserSummary deleteUser(UserId userId);

    List<UserSummary> listUsers(int pageIndex, int pageSize, String statusFilter);

    /**
     * Case-insensitive partial match on login or display name. Active users only. Requires
     * {@link SecurityPermissions#USERS_VIEW}. {@code limit} is clamped to a small positive maximum.
     */
    List<UserSummary> searchUsers(String query, int limit);

    void changeOwnPassword(char[] currentPassword, char[] newPassword);

    PasswordResetResult requestPasswordReset(UserId targetUserId);
}
