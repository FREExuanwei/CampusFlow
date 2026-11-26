package com.campusflow.migrator;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import static com.campusflow.migrator.MigrationDiagnostics.ApprovedStatement;
import static com.campusflow.migrator.MigrationDiagnostics.Operation;

final class MigrationJdbcGuard {
    // These paths are specific to the pinned Flyway MySQL and Core versions.
    private static final String ACQUIRE_LOCK = "SELECT GET_LOCK(?,10)";
    private static final String HISTORY_ROW_LOCK = "SELECT * FROM `" + MigrationConfiguration.DATABASE
            + "`.`flyway_schema_history` FOR UPDATE";
    private MigrationFailure stopped;
    private int currentVersion;

    void requireRunning() {
        if (stopped != null) {
            throw stopped;
        }
    }

    Connection wrap(Connection connection) {
        return proxy(Connection.class, (instance, method, arguments) -> {
            if (!method.getName().equals("close") && !method.getName().equals("isClosed")) {
                requireRunning();
            }
            Object result;
            try {
                result = call(connection, method, arguments);
            } catch (SQLException failure) {
                if (method.getName().equals("prepareStatement")) {
                    throw classify(failure, (String) arguments[0]);
                }
                throw failure;
            }
            return switch (method.getName()) {
                case "prepareStatement" -> wrapStatement(PreparedStatement.class, (PreparedStatement) result,
                        (String) arguments[0]);
                case "createStatement" -> wrapStatement(Statement.class, (Statement) result, null);
                default -> result;
            };
        });
    }

    private <T extends Statement> T wrapStatement(Class<T> type, T statement, String preparedSql) {
        return proxy(type, (instance, method, arguments) -> {
            if (!method.getName().startsWith("execute")) {
                return call(statement, method, arguments);
            }
            requireRunning();
            String sql = preparedSql;
            if (arguments != null && arguments.length > 0 && arguments[0] instanceof String text) {
                sql = text;
            }
            ApprovedStatement approved = ApprovedStatement.identify(sql);
            if (approved != null) {
                currentVersion = approved.version;
            }
            try {
                Object result = call(statement, method, arguments);
                return ACQUIRE_LOCK.equals(sql) && result instanceof ResultSet rows ? wrapLockResult(rows) : result;
            } catch (SQLException failure) {
                throw classify(failure, sql);
            }
        });
    }

    private Throwable classify(SQLException failure, String sql) {
        ApprovedStatement approved = ApprovedStatement.identify(sql);
        Operation operation = approved != null ? Operation.FLYWAY_MIGRATION_STATEMENT : operationOf(sql);
        boolean acquiringLock = operation == Operation.FLYWAY_ACQUIRE_LOCK;
        if (acquiringLock || operation == Operation.FLYWAY_HISTORY_CREATE_TABLE
                || operation == Operation.FLYWAY_HISTORY_CREATE_INDEX) {
            MigrationFailure.Category category = MigrationWorkflow.categoryOf(failure);
            if (category == MigrationFailure.Category.SQL_EXECUTION) {
                category = acquiringLock ? MigrationFailure.Category.LOCK : MigrationFailure.Category.HISTORY;
            }
            return stop(category, operation, failure);
        }
        int version = approved != null ? approved.version : currentVersion;
        MigrationDiagnostics.mark(failure, operation, failure, version, approved);
        return failure;
    }

    private ResultSet wrapLockResult(ResultSet rows) {
        return proxy(ResultSet.class, (instance, method, arguments) -> {
            Object value;
            try {
                value = call(rows, method, arguments);
            } catch (SQLException failure) {
                throw classify(failure, ACQUIRE_LOCK);
            }
            if (method.getName().equals("next") && Boolean.FALSE.equals(value)
                    || method.getName().equals("getInt") && !Integer.valueOf(1).equals(value)) {
                throw stop(MigrationFailure.Category.LOCK, Operation.FLYWAY_ACQUIRE_LOCK, null);
            }
            return value;
        });
    }

    private static Operation operationOf(String sql) {
        if (ACQUIRE_LOCK.equals(sql)) {
            return Operation.FLYWAY_ACQUIRE_LOCK;
        }
        if (HISTORY_ROW_LOCK.equals(sql)) {
            return Operation.FLYWAY_HISTORY_ROW_LOCK;
        }
        if (sql != null) {
            String normalized = sql.stripLeading().toUpperCase(Locale.ROOT);
            if (normalized.contains("FLYWAY_SCHEMA_HISTORY")) {
                if (normalized.startsWith("CREATE TABLE ")) {
                    return Operation.FLYWAY_HISTORY_CREATE_TABLE;
                }
                if (normalized.startsWith("CREATE INDEX ")) {
                    return Operation.FLYWAY_HISTORY_CREATE_INDEX;
                }
                if (normalized.startsWith("INSERT INTO ")) {
                    return Operation.FLYWAY_HISTORY_INSERT;
                }
            }
            if (normalized.startsWith("SELECT EVENT_NAME FROM INFORMATION_SCHEMA.EVENTS")) {
                return Operation.FLYWAY_CAPABILITY_EVENTS;
            }
            if (normalized.startsWith("SELECT VARIABLE_VALUE FROM PERFORMANCE_SCHEMA.GLOBAL_VARIABLES")) {
                return Operation.FLYWAY_CAPABILITY_PXC;
            }
            if (normalized.equals("SELECT @@GLOBAL.WSREP_ON")) {
                return Operation.FLYWAY_CAPABILITY_WSREP;
            }
            if (normalized.equals("SELECT @@GLOBAL.ENFORCE_GTID_CONSISTENCY")) {
                return Operation.FLYWAY_CAPABILITY_GTID;
            }
            if (normalized.startsWith("SELECT ") && normalized.contains("PERFORMANCE_SCHEMA.USER_VARIABLES_BY_THREAD")) {
                return Operation.FLYWAY_CAPABILITY_USER_VARIABLES;
            }
            if (normalized.equals("SHOW DATABASES LIKE 'RDSADMIN';")) {
                return Operation.FLYWAY_CAPABILITY_RDS;
            }
        }
        return Operation.FLYWAY_SQL;
    }

    private MigrationFailure stop(MigrationFailure.Category category, Operation operation, SQLException failure) {
        stopped = new MigrationFailure(category);
        MigrationDiagnostics.mark(stopped, operation, failure, currentVersion, null);
        return stopped;
    }

    private static Object call(Object target, Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
