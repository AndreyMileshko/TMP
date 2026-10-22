package com.tmp.security.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.tmp.security.domain.repository.PermissionOverrideRepository;
import com.tmp.security.domain.repository.RoleAssignmentRepository;
import com.tmp.security.domain.repository.RoleRepository;
import com.tmp.security.domain.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;

/** Shared test doubles for {@link LastAdministratorGuard}. */
final class LastAdministratorGuardFixtures {

    private LastAdministratorGuardFixtures() {
    }

    static LastAdministratorGuard guard(
            UserRepository users,
            RoleRepository roles,
            RoleAssignmentRepository assignments,
            PermissionOverrideRepository overrides) {
        return new LastAdministratorGuard(users, roles, assignments, overrides, noopJdbc());
    }

    static JdbcTemplate noopJdbc() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        java.sql.Connection connection = mock(java.sql.Connection.class);
        java.sql.PreparedStatement statement = mock(java.sql.PreparedStatement.class);
        try {
            org.mockito.Mockito.when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(statement);
            org.mockito.Mockito.when(statement.execute()).thenReturn(true);
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException(ex);
        }
        doAnswer(invocation -> {
                    org.springframework.jdbc.core.ConnectionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInConnection(connection);
                })
                .when(jdbc)
                .execute(any(org.springframework.jdbc.core.ConnectionCallback.class));
        return jdbc;
    }
}