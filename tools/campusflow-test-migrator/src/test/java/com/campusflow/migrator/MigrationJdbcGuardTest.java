package com.campusflow.migrator;

import org.flywaydb.core.internal.exception.FlywaySqlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MigrationJdbcGuardTest {
    @Test void aBusyMysqlLockStopsBeforeFlywayCanTryAgain() throws Exception {
        var attempts = new AtomicInteger();
        var guard = new MigrationJdbcGuard();
        var connection = guard.wrap(proxy(Connection.class, (proxy, method, args) -> {
            if (method.getName().equals("close")) {
                return null;
            }
            assertEquals("prepareStatement", method.getName());
            assertEquals("SELECT GET_LOCK(?,10)", args[0]);
            return proxy(PreparedStatement.class, (p, query, parameters) -> {
                assertEquals("executeQuery", query.getName());
                attempts.incrementAndGet();
                return proxy(ResultSet.class, (row, access, columns) -> switch (access.getName()) {
                    case "next" -> true;
                    case "getInt" -> 0;
                    default -> fail("unexpected lock result call");
                });
            });
        }));

        var statement = connection.prepareStatement("SELECT GET_LOCK(?,10)");
        var rows = statement.executeQuery();
        assertTrue(rows.next());
        String output = failureOutput((password, resources) -> {
            rows.getInt(1);
            return fail("busy lock should have stopped");
        });
        assertTrue(output.lines().anyMatch("操作停止 LOCK"::equals));
        assertTrue(output.lines().anyMatch("诊断 stage=FLYWAY operation=FLYWAY_ACQUIRE_LOCK flyway_phase=LOCK"::equals));
        assertThrows(MigrationFailure.class, statement::executeQuery);
        assertThrows(MigrationFailure.class, guard::requireRunning);
        assertEquals(1, attempts.get());
        connection.close();
    }

    @ParameterizedTest
    @CsvSource({
            "CREATE TABLE flyway_schema_history (installed_rank INT), FLYWAY_HISTORY_CREATE_TABLE",
            "CREATE INDEX flyway_schema_history_s_idx ON flyway_schema_history(success), FLYWAY_HISTORY_CREATE_INDEX"
    })
    void aFailedHistoryCreationStopsBeforeAnySubsequentDbStatement(String creation, String operation) throws Exception {
        var attempts = new AtomicInteger();
        var guard = new MigrationJdbcGuard();
        var connection = guard.wrap(proxy(Connection.class, (proxy, method, args) -> {
            assertEquals("createStatement", method.getName());
            return proxy(Statement.class, (p, query, parameters) -> {
                assertEquals("execute", query.getName());
                attempts.incrementAndGet();
                throw new SQLException("synthetic history error", "42000", 1142);
            });
        }));

        var statement = connection.createStatement();
        String output = failureOutput((password, resources) -> {
            statement.execute(creation);
            return fail("history creation should have stopped");
        });
        assertTrue(output.lines().anyMatch("操作停止 PERMISSION"::equals));
        assertTrue(output.lines().anyMatch(("诊断 stage=FLYWAY operation=" + operation
                + " jdbc_category=SQL_EXCEPTION mysql_errno=1142 sqlstate=42000 flyway_phase=HISTORY_CREATE")::equals));
        assertFalse(output.contains("synthetic history error"));
        assertFalse(output.contains(creation));
        assertThrows(MigrationFailure.class, () -> statement.execute(creation));
        assertThrows(MigrationFailure.class, connection::createStatement);
        assertEquals(1, attempts.get());
    }

    @Test void aDeniedHistoryRowLockKeepsItsSqlExceptionAndReportsTheOperation() throws Exception {
        var denied = new SQLException("synthetic row lock denial", "42000", 1142);
        var guard = new MigrationJdbcGuard();
        var connection = guard.wrap(proxy(Connection.class, (instance, method, args) -> {
            assertEquals("prepareStatement", method.getName());
            assertEquals("SELECT * FROM `campusflow_test`.`flyway_schema_history` FOR UPDATE", args[0]);
            return proxy(PreparedStatement.class, (statement, query, parameters) -> {
                assertEquals("execute", query.getName());
                throw denied;
            });
        }));
        var statement = connection.prepareStatement("SELECT * FROM `campusflow_test`.`flyway_schema_history` FOR UPDATE");
        String output = failureOutput((password, resources) -> {
            var actual = assertThrows(SQLException.class, statement::execute);
            assertSame(denied, actual);
            throw new FlywaySqlException("synthetic Flyway wrapper", actual);
        });
        assertTrue(output.lines().anyMatch("操作停止 PERMISSION"::equals));
        assertTrue(output.lines().anyMatch(
                "诊断 stage=FLYWAY operation=FLYWAY_HISTORY_ROW_LOCK jdbc_category=SQL_EXCEPTION mysql_errno=1142 sqlstate=42000 flyway_phase=LOCK"::equals));
        assertFalse(output.contains("synthetic"));
        assertFalse(output.contains("FOR UPDATE"));
        assertDoesNotThrow(guard::requireRunning);
    }

    @Test void everyApprovedStatementHasItsOwnFixedIdentifier() throws Exception {
        var resources = ApprovedMigrations.load(getClass().getClassLoader());
        var actual = new ArrayList<MigrationDiagnostics.ApprovedStatement>();
        int version = 0;
        for (var resource : resources.all()) {
            version++;
            try (var reader = new java.io.BufferedReader(resource.read())) {
                String sql = String.join("\n", reader.lines().toList());
                for (String statement : sql.split(";")) {
                    if (!statement.isBlank()) {
                        var id = MigrationDiagnostics.ApprovedStatement.identify(statement);
                        assertNotNull(id, resource::getFilename);
                        assertEquals(version, id.version);
                        actual.add(id);
                    }
                }
            }
        }
        assertEquals(List.of(MigrationDiagnostics.ApprovedStatement.values()), actual);
    }

    @Test void aDeniedV4StatementReportsOnlyTheApprovedVersionAndIdentifier() throws Exception {
        var denied = new SQLSyntaxErrorException("synthetic statement denial", "42000", 1142);
        var guard = new MigrationJdbcGuard();
        var connection = guard.wrap(proxy(Connection.class, (instance, method, args) ->
                proxy(Statement.class, (statement, query, parameters) -> { throw denied; })));
        var statement = connection.createStatement();
        String output = failureOutput((password, resources) -> {
            statement.execute("ALTER TABLE activities ADD CONSTRAINT fk_activities_reviewer synthetic");
            return fail("execution should have stopped");
        });
        assertTrue(output.lines().anyMatch(("诊断 stage=FLYWAY operation=FLYWAY_MIGRATION_STATEMENT"
                + " jdbc_category=SYNTAX_OR_ACCESS_RULE mysql_errno=1142 sqlstate=42000"
                + " flyway_phase=MIGRATION_SQL migration_version=V4 statement_id=V4_ADD_REVIEWER_FK")::equals));
        assertFalse(output.contains("synthetic"));
        assertFalse(output.contains("ALTER TABLE"));
    }

    @Test void anOptionalProbeCanBeHandledByFlywayWithoutBlockingTheSession() throws Exception {
        var denied = new SQLException("synthetic optional probe denial", "42000", 1142);
        var guard = new MigrationJdbcGuard();
        var connection = guard.wrap(proxy(Connection.class, (instance, method, args) ->
                proxy(PreparedStatement.class, (statement, query, parameters) -> { throw denied; })));
        var statement = connection.prepareStatement("SELECT @@GLOBAL.WSREP_ON");
        assertSame(denied, assertThrows(SQLException.class, statement::executeQuery));
        assertDoesNotThrow(guard::requireRunning);
    }

    private static String failureOutput(MigrationWorkflow.Action action) {
        var output = new ByteArrayOutputStream();
        int exit = MigrationWorkflow.run(new String[0],
                () -> ApprovedMigrations.load(MigrationJdbcGuardTest.class.getClassLoader()),
                () -> "fixture".toCharArray(), () -> "MIGRATE campusflow_test V1-V6", action,
                new PrintStream(output, true, StandardCharsets.UTF_8));
        assertEquals(3, exit);
        return output.toString(StandardCharsets.UTF_8);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
