package com.campusflow.migrator;

import com.mysql.cj.jdbc.MysqlDataSource;

import java.io.Serial;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

import static com.campusflow.migrator.MigrationDiagnostics.Operation.MYSQL_CONNECT;

final class FixedMysqlDataSource extends MysqlDataSource {
    @Serial
    private static final long serialVersionUID = 1L;

    private final MigrationJdbcGuard guard = new MigrationJdbcGuard();

    FixedMysqlDataSource(char[] password) {
        setUrl(MigrationConfiguration.URL);
        setUser(MigrationConfiguration.USER);
        setPassword(new String(password));
    }

    @Override
    public Connection getConnection(String user, String password) throws SQLException {
        if (!MigrationConfiguration.USER.equals(user) || !Objects.equals(getPassword(), password)) {
            throw new MigrationFailure(MigrationFailure.Category.CONFIGURATION);
        }
        return super.getConnection(user, password);
    }

    @Override
    protected Connection getConnection(Properties properties) throws SQLException {
        guard.requireRunning();
        if (!MigrationConfiguration.URL.equals(getUrl()) || !MigrationConfiguration.USER.equals(getUser())) {
            throw new MigrationFailure(MigrationFailure.Category.CONFIGURATION);
        }
        Connection connection;
        try {
            connection = super.getConnection(properties);
        } catch (SQLException | RuntimeException failure) {
            MigrationDiagnostics.mark(failure, MYSQL_CONNECT);
            throw failure;
        }
        try {
            PostMigrationVerifier.requireMigrationSession(connection);
            return guard.wrap(connection);
        } catch (SQLException | RuntimeException failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }
}
