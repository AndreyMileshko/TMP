package com.tmp.security.api;

import java.util.List;

/**
 * Public user administration API.
 */
public interface UserAdministrationService {

    UserSummary createUser(Login login, DisplayName displayName);

    UserSummary updateUser(UserId userId, Login login, DisplayName displayName);

    UserSummary deleteUser(UserId userId);

    List<UserSummary> listUsers(int pageIndex, int pageSize, String statusFilter);

    void changeOwnPassword(char[] currentPassword, char[] newPassword);

    void requestPasswordReset(UserId targetUserId);
}
