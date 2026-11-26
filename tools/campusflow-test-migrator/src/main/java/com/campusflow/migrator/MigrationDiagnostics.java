package com.campusflow.migrator;

import org.flywaydb.core.api.exception.FlywayValidateException;
import org.flywaydb.core.internal.exception.FlywayMigrateException;

import java.io.PrintStream;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransactionRollbackException;
import java.sql.SQLTransientConnectionException;
import java.util.Locale;
import java.util.Set;

final class MigrationDiagnostics {
    private enum Stage { EXECUTION, JDBC, SESSION, FLYWAY }

    private enum PermissionCheck { CURRENT_ROLE, MIGRATOR_GRANTS }

    private enum Phase { SETUP, VALIDATE, HISTORY_CREATE, LOCK, MIGRATION_SQL, HISTORY_WRITE }

    enum Operation {
        MIGRATION_ACTION(Stage.EXECUTION),
        MYSQL_CONNECT(Stage.JDBC),
        SESSION_TARGET(Stage.SESSION),
        SESSION_TLS(Stage.SESSION),
        SESSION_CURRENT_ROLE(Stage.SESSION, PermissionCheck.CURRENT_ROLE),
        SESSION_MIGRATOR_GRANTS(Stage.SESSION, PermissionCheck.MIGRATOR_GRANTS),
        SESSION_CANDIDATE_LOCK(Stage.SESSION),
        SESSION_EMPTY_SCHEMA(Stage.SESSION),
        FLYWAY_MIGRATE(Phase.SETUP),
        FLYWAY_SQL(Phase.SETUP),
        FLYWAY_ACQUIRE_LOCK(Phase.LOCK),
        FLYWAY_HISTORY_CREATE_TABLE(Phase.HISTORY_CREATE),
        FLYWAY_HISTORY_CREATE_INDEX(Phase.HISTORY_CREATE),
        FLYWAY_HISTORY_ROW_LOCK(Phase.LOCK),
        FLYWAY_HISTORY_INSERT(Phase.HISTORY_WRITE),
        FLYWAY_MIGRATION_STATEMENT(Phase.MIGRATION_SQL),
        FLYWAY_CAPABILITY_EVENTS(Phase.SETUP),
        FLYWAY_CAPABILITY_PXC(Phase.SETUP),
        FLYWAY_CAPABILITY_WSREP(Phase.SETUP),
        FLYWAY_CAPABILITY_GTID(Phase.SETUP),
        FLYWAY_CAPABILITY_USER_VARIABLES(Phase.SETUP),
        FLYWAY_CAPABILITY_RDS(Phase.SETUP);

        private final Stage stage;
        private final PermissionCheck permissionCheck;
        private final Phase phase;

        Operation(Stage stage) {
            this(stage, null);
        }

        Operation(Stage stage, PermissionCheck permissionCheck) {
            this.stage = stage;
            this.permissionCheck = permissionCheck;
            this.phase = null;
        }

        Operation(Phase phase) {
            this.stage = Stage.FLYWAY;
            this.permissionCheck = null;
            this.phase = phase;
        }
    }

    enum ApprovedStatement {
        V1_CREATE_USERS(1, "CREATE TABLE USERS ("),
        V1_CREATE_ROLES(1, "CREATE TABLE ROLES ("),
        V1_CREATE_USER_ROLES(1, "CREATE TABLE USER_ROLES ("),
        V2_INSERT_BASE_ROLES(2, "INSERT INTO ROLES ("),
        V3_CREATE_ACTIVITIES(3, "CREATE TABLE ACTIVITIES ("),
        V4_ADD_REVIEWED_BY(4, "ALTER TABLE ACTIVITIES ADD COLUMN REVIEWED_BY "),
        V4_ADD_REVIEWED_AT(4, "ALTER TABLE ACTIVITIES ADD COLUMN REVIEWED_AT "),
        V4_ADD_REVIEW_REASON(4, "ALTER TABLE ACTIVITIES ADD COLUMN REVIEW_REASON "),
        V4_ADD_PUBLISHED_AT(4, "ALTER TABLE ACTIVITIES ADD COLUMN PUBLISHED_AT "),
        V4_ADD_REVIEWER_FK(4, "ALTER TABLE ACTIVITIES ADD CONSTRAINT FK_ACTIVITIES_REVIEWER "),
        V4_ADD_STATUS_CHECK(4, "ALTER TABLE ACTIVITIES ADD CONSTRAINT CK_ACTIVITIES_STATUS "),
        V5_CREATE_ENROLLMENTS(5, "CREATE TABLE ENROLLMENTS ("),
        V6_CREATE_NOTIFICATIONS(6, "CREATE TABLE NOTIFICATIONS ("),
        V6_CREATE_OPERATION_LOGS(6, "CREATE TABLE OPERATION_LOGS (");

        final int version;
        private final String prefix;

        ApprovedStatement(int version, String prefix) {
            this.version = version;
            this.prefix = prefix;
        }

        static ApprovedStatement identify(String sql) {
            if (sql == null) {
                return null;
            }
            String normalized = sql.stripLeading().toUpperCase(Locale.ROOT);
            for (ApprovedStatement statement : values()) {
                if (normalized.startsWith(statement.prefix)) {
                    return statement;
                }
            }
            return null;
        }
    }

    private static final Set<String> SQL_STATES = Set.of(
            "08001", "08004", "08006", "08S01", "23000", "28000", "40001",
            "42000", "42S01", "42S02", "HY000", "HY008", "HYT00", "HYT01");

    private enum JdbcCategory {
        CONNECTION, DATA, FEATURE_NOT_SUPPORTED, INTEGRITY_CONSTRAINT, INVALID_AUTHORIZATION,
        SYNTAX_OR_ACCESS_RULE, TIMEOUT, TRANSACTION_ROLLBACK, SQL_EXCEPTION
    }

    private record MysqlError(int errno, String sqlState, JdbcCategory category) {}

    // Flyway's control flow depends on JDBC exception types. Keep the original exception and its cause chain.
    private static final class Location extends RuntimeException {
        private final Operation operation;
        private final MysqlError mysqlError;
        private final int version;
        private final ApprovedStatement statement;

        private Location(Operation operation, MysqlError mysqlError, int version, ApprovedStatement statement) {
            super(null, null, false, false);
            this.operation = operation;
            this.mysqlError = mysqlError;
            this.version = version;
            this.statement = statement;
        }
    }

    static void mark(Throwable failure, Operation operation) {
        mark(failure, operation, failure);
    }

    static void mark(Throwable failure, Operation operation, Throwable errorSource) {
        mark(failure, operation, errorSource, 0, null);
    }

    static void mark(Throwable failure, Operation operation, Throwable errorSource,
                     int version, ApprovedStatement statement) {
        failure.addSuppressed(new Location(operation, mysqlErrorOf(errorSource), version, statement));
    }

    static void print(PrintStream output, Throwable failure) {
        Location location = locate(failure);
        Operation operation = location.operation;
        var line = new StringBuilder("诊断 stage=").append(operation.stage).append(" operation=").append(operation);
        if (operation.permissionCheck != null) {
            line.append(" permission_check=").append(operation.permissionCheck);
        }
        if (location.mysqlError.category() != null) {
            line.append(" jdbc_category=").append(location.mysqlError.category());
        }
        if (location.mysqlError.errno() != 0) {
            line.append(" mysql_errno=").append(location.mysqlError.errno());
        }
        if (location.mysqlError.sqlState() != null) {
            line.append(" sqlstate=").append(location.mysqlError.sqlState());
        }
        if (operation.phase != null) {
            line.append(" flyway_phase=").append(phaseOf(failure, operation));
            int version = location.version > 0 ? location.version : migrationVersionOf(failure);
            if (version > 0) {
                line.append(" migration_version=V").append(version);
            }
        }
        if (location.statement != null) {
            line.append(" statement_id=").append(location.statement);
        }
        output.println(line);
    }

    private static Location locate(Throwable failure) {
        Location fallback = null;
        Throwable cause = failure;
        for (int depth = 0; depth < 16 && cause != null; depth++, cause = cause.getCause()) {
            for (Throwable suppressed : cause.getSuppressed()) {
                if (suppressed instanceof Location location) {
                    if (location.operation != Operation.FLYWAY_MIGRATE && location.operation != Operation.FLYWAY_SQL) {
                        return location;
                    }
                    fallback = location;
                }
            }
        }
        return fallback != null ? fallback : new Location(Operation.MIGRATION_ACTION, mysqlErrorOf(failure), 0, null);
    }

    private static Phase phaseOf(Throwable failure, Operation operation) {
        if (operation.phase == Phase.SETUP) {
            for (int depth = 0; depth < 16 && failure != null; depth++, failure = failure.getCause()) {
                if (failure instanceof FlywayValidateException) {
                    return Phase.VALIDATE;
                }
                if (failure instanceof FlywayMigrateException) {
                    return Phase.MIGRATION_SQL;
                }
            }
        }
        return operation.phase;
    }

    private static int migrationVersionOf(Throwable failure) {
        for (int depth = 0; depth < 16 && failure != null; depth++, failure = failure.getCause()) {
            if (failure instanceof FlywayMigrateException migrate && migrate.getMigration() != null
                    && migrate.getMigration().getVersion() != null) {
                String version = migrate.getMigration().getVersion().getVersion();
                if (version.length() == 1 && version.charAt(0) >= '1' && version.charAt(0) <= '6') {
                    return version.charAt(0) - '0';
                }
            }
        }
        return 0;
    }

    private static MysqlError mysqlErrorOf(Throwable failure) {
        MysqlError fallback = new MysqlError(0, null, null);
        for (int depth = 0; depth < 16 && failure != null; depth++, failure = failure.getCause()) {
            SQLException sql = failure instanceof SQLException exception ? exception : null;
            for (int chained = 0; chained < 8 && sql != null; chained++, sql = sql.getNextException()) {
                String state = sql.getSQLState();
                String safeState = state != null && SQL_STATES.contains(state) ? state : null;
                int errno = sql.getErrorCode();
                if (errno > 0 && errno <= 0xFFFF) {
                    return new MysqlError(errno, safeState, jdbcCategoryOf(sql));
                }
                if (fallback.category() == null || fallback.sqlState() == null) {
                    fallback = new MysqlError(0, safeState, jdbcCategoryOf(sql));
                }
            }
        }
        return fallback;
    }

    private static JdbcCategory jdbcCategoryOf(SQLException sql) {
        return switch (sql) {
            case SQLInvalidAuthorizationSpecException ignored -> JdbcCategory.INVALID_AUTHORIZATION;
            case SQLSyntaxErrorException ignored -> JdbcCategory.SYNTAX_OR_ACCESS_RULE;
            case SQLTimeoutException ignored -> JdbcCategory.TIMEOUT;
            case SQLIntegrityConstraintViolationException ignored -> JdbcCategory.INTEGRITY_CONSTRAINT;
            case SQLTransactionRollbackException ignored -> JdbcCategory.TRANSACTION_ROLLBACK;
            case SQLFeatureNotSupportedException ignored -> JdbcCategory.FEATURE_NOT_SUPPORTED;
            case SQLDataException ignored -> JdbcCategory.DATA;
            case SQLNonTransientConnectionException ignored -> JdbcCategory.CONNECTION;
            case SQLTransientConnectionException ignored -> JdbcCategory.CONNECTION;
            case SQLRecoverableException ignored -> JdbcCategory.CONNECTION;
            default -> JdbcCategory.SQL_EXCEPTION;
        };
    }

    private MigrationDiagnostics() {}
}
