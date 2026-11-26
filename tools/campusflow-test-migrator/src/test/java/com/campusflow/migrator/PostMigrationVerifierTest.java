package com.campusflow.migrator;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.campusflow.migrator.MigrationWorkflow.Check;
import static com.campusflow.migrator.MigrationWorkflow.Finding;
import static com.campusflow.migrator.MigrationWorkflow.Status;
import static org.junit.jupiter.api.Assertions.*;

class PostMigrationVerifierTest {
    private final Map<String, List<List<String>>> answers = new HashMap<>();
    private final List<String> queries = new ArrayList<>();
    private final ApprovedMigrations migrations = ApprovedMigrations.load(getClass().getClassLoader());

    PostMigrationVerifierTest() {
        answers.put("session", records("campusflow_test|campusflow_example_migrator@127.0.0.1|3306"));
        answers.put("tls", records("Ssl_cipher|TLS_AES_256_GCM_SHA384"));
        answers.put("active-role", records("NONE"));
        answers.put("other-objects", List.of());
        answers.put("candidate-lock", records("1"));
        answers.put("migrator-grants", records("""
                GRANT USAGE ON *.* TO `campusflow_example_migrator`@`127.0.0.1`
                GRANT REFERENCES, CREATE ON `campusflow_test`.`users` TO 'campusflow_example_migrator'@'127.0.0.1'
                GRANT INSERT, CREATE, REFERENCES ON `campusflow_test`.`roles` TO 'campusflow_example_migrator'@'127.0.0.1'
                GRANT CREATE ON `campusflow_test`.`user_roles` TO 'campusflow_example_migrator'@'127.0.0.1'
                GRANT INSERT, ALTER, CREATE, REFERENCES ON `campusflow_test`.`activities` TO 'campusflow_example_migrator'@'127.0.0.1'
                GRANT CREATE ON `campusflow_test`.`enrollments` TO 'campusflow_example_migrator'@'127.0.0.1'
                GRANT CREATE ON `campusflow_test`.`notifications` TO 'campusflow_example_migrator'@'127.0.0.1'
                GRANT CREATE ON `campusflow_test`.`operation_logs` TO 'campusflow_example_migrator'@'127.0.0.1'
                GRANT SELECT, INSERT, UPDATE, CREATE, INDEX ON `campusflow_test`.`flyway_schema_history` TO 'campusflow_example_migrator'@'127.0.0.1'
                """));
        answers.put("tables", records("""
                users|BASE TABLE|InnoDB
                roles|BASE TABLE|InnoDB
                user_roles|BASE TABLE|InnoDB
                activities|BASE TABLE|InnoDB
                enrollments|BASE TABLE|InnoDB
                notifications|BASE TABLE|InnoDB
                operation_logs|BASE TABLE|InnoDB
                flyway_schema_history|BASE TABLE|InnoDB
                """));
        answers.put("columns", records("""
                users|id|bigint|NO|auto_increment
                users|username|varchar(32)|NO|
                users|password_hash|varchar(60)|NO|
                users|display_name|varchar(80)|NO|
                users|status|varchar(16)|NO|
                users|created_at|datetime(6)|NO|
                users|updated_at|datetime(6)|NO|
                roles|id|bigint|NO|auto_increment
                roles|role_code|varchar(32)|NO|
                roles|role_name|varchar(64)|NO|
                user_roles|user_id|bigint|NO|
                user_roles|role_id|bigint|NO|
                activities|id|bigint|NO|auto_increment
                activities|title|varchar(120)|NO|
                activities|description|varchar(2000)|NO|
                activities|location|varchar(160)|NO|
                activities|starts_at|datetime(6)|NO|
                activities|ends_at|datetime(6)|NO|
                activities|capacity|int|NO|
                activities|status|varchar(16)|NO|
                activities|created_by|bigint|NO|
                activities|created_at|datetime(6)|NO|
                activities|updated_at|datetime(6)|NO|
                activities|version|bigint|NO|
                activities|reviewed_by|bigint|YES|
                activities|reviewed_at|datetime(6)|YES|
                activities|review_reason|varchar(500)|YES|
                activities|published_at|datetime(6)|YES|
                enrollments|id|bigint|NO|auto_increment
                enrollments|activity_id|bigint|NO|
                enrollments|user_id|bigint|NO|
                enrollments|status|varchar(16)|NO|
                enrollments|enrolled_at|datetime(6)|NO|
                enrollments|cancelled_at|datetime(6)|YES|
                notifications|id|bigint|NO|auto_increment
                notifications|user_id|bigint|NO|
                notifications|activity_id|bigint|NO|
                notifications|type|varchar(32)|NO|
                notifications|created_at|datetime(6)|NO|
                notifications|read_at|datetime(6)|YES|
                operation_logs|id|bigint|NO|auto_increment
                operation_logs|actor_id|bigint|NO|
                operation_logs|action|varchar(32)|NO|
                operation_logs|target_type|varchar(16)|NO|
                operation_logs|target_id|bigint|NO|
                operation_logs|created_at|datetime(6)|NO|
                flyway_schema_history|installed_rank|int|NO|
                flyway_schema_history|version|varchar(50)|YES|
                flyway_schema_history|description|varchar(200)|NO|
                flyway_schema_history|type|varchar(20)|NO|
                flyway_schema_history|script|varchar(1000)|NO|
                flyway_schema_history|checksum|int|YES|
                flyway_schema_history|installed_by|varchar(100)|NO|
                flyway_schema_history|installed_on|timestamp|NO|DEFAULT_GENERATED
                flyway_schema_history|execution_time|int|NO|
                flyway_schema_history|success|tinyint(1)|NO|
                """));
        answers.put("indexes", records("""
                users|PRIMARY|0|1|id|BTREE|
                users|uk_users_username|0|1|username|BTREE|
                roles|PRIMARY|0|1|id|BTREE|
                roles|uk_roles_role_code|0|1|role_code|BTREE|
                user_roles|PRIMARY|0|1|user_id|BTREE|
                user_roles|PRIMARY|0|2|role_id|BTREE|
                user_roles|ix_user_roles_role_user|1|1|role_id|BTREE|
                user_roles|ix_user_roles_role_user|1|2|user_id|BTREE|
                flyway_schema_history|PRIMARY|0|1|installed_rank|BTREE|
                flyway_schema_history|flyway_schema_history_s_idx|1|1|success|BTREE|
                activities|PRIMARY|0|1|id|BTREE|
                activities|ix_activities_status_starts|1|1|status|BTREE|
                activities|ix_activities_status_starts|1|2|starts_at|BTREE|
                activities|ix_activities_status_starts|1|3|id|BTREE|
                activities|fk_activities_creator|1|1|created_by|BTREE|
                activities|fk_activities_reviewer|1|1|reviewed_by|BTREE|
                enrollments|PRIMARY|0|1|id|BTREE|
                enrollments|uk_enrollments_activity_user|0|1|activity_id|BTREE|
                enrollments|uk_enrollments_activity_user|0|2|user_id|BTREE|
                enrollments|ix_enrollments_activity_status|1|1|activity_id|BTREE|
                enrollments|ix_enrollments_activity_status|1|2|status|BTREE|
                enrollments|ix_enrollments_user_id|1|1|user_id|BTREE|
                enrollments|ix_enrollments_user_id|1|2|id|BTREE|
                notifications|PRIMARY|0|1|id|BTREE|
                notifications|ix_notifications_user_id|1|1|user_id|BTREE|
                notifications|ix_notifications_user_id|1|2|id|BTREE|
                notifications|fk_notifications_activity|1|1|activity_id|BTREE|
                operation_logs|PRIMARY|0|1|id|BTREE|
                operation_logs|ix_operation_logs_action_id|1|1|action|BTREE|
                operation_logs|ix_operation_logs_action_id|1|2|id|BTREE|
                operation_logs|ix_operation_logs_actor_id|1|1|actor_id|BTREE|
                operation_logs|ix_operation_logs_actor_id|1|2|id|BTREE|
                """));
        answers.put("foreign-keys", records("""
                user_roles|fk_user_roles_user|user_id|campusflow_test|users|id|NO ACTION|RESTRICT
                user_roles|fk_user_roles_role|role_id|campusflow_test|roles|id|NO ACTION|RESTRICT
                activities|fk_activities_creator|created_by|campusflow_test|users|id|NO ACTION|RESTRICT
                activities|fk_activities_reviewer|reviewed_by|campusflow_test|users|id|NO ACTION|RESTRICT
                enrollments|fk_enrollments_activity|activity_id|campusflow_test|activities|id|NO ACTION|RESTRICT
                enrollments|fk_enrollments_user|user_id|campusflow_test|users|id|NO ACTION|RESTRICT
                notifications|fk_notifications_user|user_id|campusflow_test|users|id|NO ACTION|RESTRICT
                notifications|fk_notifications_activity|activity_id|campusflow_test|activities|id|NO ACTION|RESTRICT
                operation_logs|fk_operation_logs_actor|actor_id|campusflow_test|users|id|NO ACTION|RESTRICT
                """));
        answers.put("check", records("""
                users|ck_users_status|YES|((case `status` when _utf8mb4\\'ACTIVE\\' then 1 when _utf8mb4\\'DISABLED\\' then 1 else 0 end) = 1)
                activities|ck_activities_schedule|YES|ENDS_AT>STARTS_AT
                activities|ck_activities_capacity|YES|CAPACITY>0
                activities|ck_activities_status|YES|CASESTATUSWHEN'DRAFT'THEN1WHEN'SUBMITTED'THEN1WHEN'APPROVED'THEN1WHEN'REJECTED'THEN1WHEN'PUBLISHED'THEN1ELSE0END=1
                enrollments|ck_enrollments_status|YES|CASESTATUSWHEN'ENROLLED'THENCASEWHENCANCELLED_ATISNULLTHEN1ELSE0ENDWHEN'CANCELLED'THENCASEWHENCANCELLED_ATISNOTNULLTHEN1ELSE0ENDELSE0END=1
                """));
        answers.put("history", records("""
                1|1|V1__create_user_tables.sql|SQL|750175495|PASS
                2|2|V2__seed_base_roles.sql|SQL|1487491732|PASS
                3|3|V3__create_activities.sql|SQL|1039476209|PASS
                4|4|V4__activity_review_and_publication.sql|SQL|-546871031|PASS
                5|5|V5__create_enrollments.sql|SQL|444130217|PASS
                6|6|V6__notifications_and_operation_logs.sql|SQL|1931828097|PASS
                """));
    }

    @Test void acceptsTheApprovedSchemaAndAccountEvidence() throws Exception {
        PostMigrationVerifier.requireMigrationSession(connection(Set.of(), null));
        var results = PostMigrationVerifier.inspect(connection(Set.of(), null), migrations);
        assertEquals(Check.values().length, results.size());
        assertEquals(List.of(Check.ROLE_SEEDS, Check.APPLICATION_GRANTS, Check.ROLE_INHERITANCE),
                results.stream().filter(item -> item.status() != Status.PASSED).map(Finding::check).toList(),
                results::toString);
    }

    @Test void rejectsExistingHistoryOrBusinessTablesBeforeWriting() {
        var error = assertThrows(MigrationFailure.class,
                () -> PostMigrationVerifier.requireEmptySchema(connection(Set.of(), null)));
        assertEquals(MigrationFailure.Category.HISTORY, error.category());
        answers.put("tables", List.of());
        assertDoesNotThrow(() -> PostMigrationVerifier.requireEmptySchema(connection(Set.of(), null)));
        answers.put("other-objects", records("PRESENT"));
        assertThrows(MigrationFailure.class,
                () -> PostMigrationVerifier.requireEmptySchema(connection(Set.of(), null)));
    }

    @Test void createOnlyTablesNeedDbaVisibilityButVisibleDifferencesStillFail() {
        Set<String> createOnly = Set.of("user_roles", "enrollments", "notifications", "operation_logs");
        for (String query : List.of("columns", "foreign-keys")) {
            answers.put(query, answers.get(query).stream()
                    .filter(row -> !createOnly.contains(row.getFirst())).toList());
        }
        var limited = PostMigrationVerifier.inspect(connection(Set.of(), null), migrations);
        assertTrue(limited.contains(new Finding(Check.COLUMNS, Status.ADMIN_REQUIRED)));
        assertTrue(limited.contains(new Finding(Check.FOREIGN_KEYS, Status.ADMIN_REQUIRED)));
        var wrongColumns = new ArrayList<>(answers.get("columns"));
        wrongColumns.add(List.of("users", "unexpected", "int", "YES", ""));
        answers.put("columns", wrongColumns);
        var inconsistent = PostMigrationVerifier.inspect(connection(Set.of(), null), migrations);
        assertTrue(inconsistent.contains(new Finding(Check.COLUMNS, Status.MISMATCH)));
    }

    @Test void rejectsAConcurrentCandidateBeforeLookingAtSchemaObjects() {
        answers.put("candidate-lock", records("0"));
        var error = assertThrows(MigrationFailure.class,
                () -> PostMigrationVerifier.requireCandidateLock(connection(Set.of(), null)));
        assertEquals(MigrationFailure.Category.LOCK, error.category());
        assertEquals(List.of("candidate-lock"), queries);
    }

    @Test void failsClosedOnAbsentV6Grant() {
        answers.put("migrator-grants", answers.get("migrator-grants").stream()
                .filter(row -> !row.getFirst().contains("`operation_logs`")).toList());
        assertTrue(sessionFailureOutput(Set.of()).contains("permission_check=MIGRATOR_GRANTS"));
    }

    @Test void rejectsAnUnenforcedV5CheckAndAModifiedV6HistoryChecksum() {
        var checks = new ArrayList<>(answers.get("check"));
        var enrollment = checks.getLast();
        checks.set(checks.size() - 1, List.of(enrollment.get(0), enrollment.get(1), "NO", enrollment.get(3)));
        answers.put("check", checks);
        var history = new ArrayList<>(answers.get("history"));
        history.set(5, records("6|6|V6__notifications_and_operation_logs.sql|SQL|0|PASS").getFirst());
        answers.put("history", history);
        var results = PostMigrationVerifier.inspect(connection(Set.of(), null), migrations);
        assertTrue(results.contains(new Finding(Check.CHECK_CONSTRAINT, Status.MISMATCH)));
        assertTrue(results.contains(new Finding(Check.HISTORY, Status.MISMATCH)));
    }

    @Test void stopsBeforeOtherChecksIfTheServerSelectsAnUnexpectedDatabase() {
        answers.put("session", records("campusflow_db|campusflow_example_migrator@127.0.0.1|3306"));
        assertEquals(MigrationFailure.Category.CONFIGURATION, assertThrows(MigrationFailure.class,
                () -> PostMigrationVerifier.requireMigrationSession(connection(Set.of(), null))).category());
        assertEquals(List.of("session"), queries);
    }

    @Test void rejectsExtraGlobalPrivilegesBeforeTheMigration() {
        var extra = new ArrayList<>(answers.get("migrator-grants"));
        extra.add(List.of("GRANT SELECT ON *.* TO 'campusflow_example_migrator'@'127.0.0.1'"));
        answers.put("migrator-grants", extra);
        String output = sessionFailureOutput(Set.of());
        assertTrue(output.lines().anyMatch(
                "诊断 stage=SESSION operation=SESSION_MIGRATOR_GRANTS permission_check=MIGRATOR_GRANTS"::equals));
        assertFalse(output.contains("GRANT SELECT"));
    }

    @Test void reportsWhichPermissionCheckRejectedAnActiveRole() {
        answers.put("active-role", records("synthetic-private-role"));
        String output = sessionFailureOutput(Set.of());
        assertTrue(output.lines().anyMatch(
                "诊断 stage=SESSION operation=SESSION_CURRENT_ROLE permission_check=CURRENT_ROLE"::equals));
        assertFalse(output.contains("synthetic-private-role"));
        assertEquals(List.of("session", "tls", "active-role"), queries);
    }

    @Test void reportsSafeMysqlFieldsWhenTheMigratorGrantQueryIsDenied() {
        String output = sessionFailureOutput(Set.of("migrator-grants"));
        assertTrue(output.lines().anyMatch(("诊断 stage=SESSION operation=SESSION_MIGRATOR_GRANTS"
                + " permission_check=MIGRATOR_GRANTS jdbc_category=SQL_EXCEPTION mysql_errno=1142 sqlstate=42000")::equals));
        assertFalse(output.contains("synthetic denied result"));
        assertFalse(output.contains("SHOW GRANTS"));
    }

    @Test void flagsTheChecksOutsideTheMigratorGrantsWithoutQueryingThem() {
        var results = PostMigrationVerifier.inspect(connection(Set.of(), null), migrations);
        assertEquals(List.of(Check.ROLE_SEEDS, Check.APPLICATION_GRANTS, Check.ROLE_INHERITANCE),
                results.stream().filter(item -> item.status() == Status.ADMIN_REQUIRED).map(Finding::check).toList());
        assertTrue(results.contains(new Finding(Check.HISTORY, Status.PASSED)));
    }

    @Test void reportsHistoryAndForeignKeyDifferences() {
        answers.put("history", records("""
                1|1|V1__create_user_tables.sql|SQL|750175495|PASS
                2|2|V2__seed_base_roles.sql|SQL|0|FAIL
                """));
        answers.put("foreign-keys", records("""
                user_roles|fk_user_roles_user|user_id|campusflow_test|users|id|RESTRICT|RESTRICT
                user_roles|fk_user_roles_role|role_id|another_schema|roles|id|RESTRICT|RESTRICT
                """));
        var results = PostMigrationVerifier.inspect(connection(Set.of(), null), migrations);
        assertTrue(results.contains(new Finding(Check.HISTORY, Status.MISMATCH)));
        assertTrue(results.contains(new Finding(Check.FOREIGN_KEYS, Status.MISMATCH)));
    }

    @Test void stopsTheReadOnlyChecksWhenAConnectionIsLost() {
        var results = PostMigrationVerifier.inspect(connection(Set.of(), "columns"), migrations);
        assertEquals(new Finding(Check.COLUMNS, Status.UNAVAILABLE), results.getLast());
        assertEquals("columns", queries.getLast());
    }

    @Test void rejectsAUniquePrefixIndexEvenWhenItsNameAndColumnMatch() {
        var indexes = new ArrayList<>(answers.get("indexes"));
        indexes.set(1, List.of("users", "uk_users_username", "0", "1", "username", "BTREE", "8"));
        answers.put("indexes", indexes);
        assertTrue(PostMigrationVerifier.inspect(connection(Set.of(), null), migrations)
                .contains(new Finding(Check.INDEXES, Status.MISMATCH)));
    }

    private String sessionFailureOutput(Set<String> denied) {
        var output = new ByteArrayOutputStream();
        int exit = MigrationWorkflow.run(new String[0], () -> migrations, () -> "fixture".toCharArray(),
                () -> "MIGRATE campusflow_test V1-V6", (password, resources) -> {
                    PostMigrationVerifier.requireMigrationSession(connection(denied, null));
                    return fail("migration session should have stopped");
                }, new PrintStream(output, true, StandardCharsets.UTF_8));
        assertEquals(3, exit);
        String messages = output.toString(StandardCharsets.UTF_8);
        assertTrue(messages.lines().anyMatch("操作停止 PERMISSION"::equals));
        return messages;
    }

    private Connection connection(Set<String> denied, String interrupted) {
        return proxy(Connection.class, (instance, method, arguments) -> switch (method.getName()) {
            case "createStatement" -> statement(denied, interrupted);
            case "close" -> null;
            default -> fail("unexpected connection method " + method.getName());
        });
    }

    private Statement statement(Set<String> denied, String interrupted) {
        return proxy(Statement.class, (instance, method, arguments) -> {
            if (method.getName().equals("close")) {
                return null;
            }
            assertEquals("executeQuery", method.getName());
            String query = ((String) arguments[0]).strip().toLowerCase(Locale.ROOT);
            assertFalse(query.contains("campusflow_db"));
            String key = queryKey(query);
            queries.add(key);
            if (denied.contains(key)) {
                throw new SQLException("synthetic denied result", "42000", 1142);
            }
            if (key.equals(interrupted)) {
                throw new SQLException("synthetic timeout", "08S01");
            }
            return resultSet(answers.get(key));
        });
    }

    private static String queryKey(String sql) {
        if (sql.startsWith("select database()")) return "session";
        if (sql.startsWith("show session status")) return "tls";
        if (sql.equals("select current_role()")) return "active-role";
        if (sql.equals("show grants for current_user")) return "migrator-grants";
        assertTrue(sql.contains("campusflow_test"));
        if (sql.contains("from information_schema.tables")) return "tables";
        if (sql.contains("from information_schema.columns")) return "columns";
        if (sql.contains("from information_schema.statistics")) return "indexes";
        if (sql.contains("from information_schema.key_column_usage")) return "foreign-keys";
        if (sql.contains("join information_schema.check_constraints")) return "check";
        if (sql.contains("from campusflow_test.flyway_schema_history")) return "history";
        if (sql.startsWith("select get_lock(")) return "candidate-lock";
        if (sql.contains("from information_schema.routines")) return "other-objects";
        return fail("unexpected synthetic query");
    }

    private static ResultSet resultSet(List<List<String>> rows) {
        assertNotNull(rows);
        int[] cursor = {-1};
        return proxy(ResultSet.class, (instance, method, arguments) -> switch (method.getName()) {
            case "next" -> ++cursor[0] < rows.size();
            case "getString" -> rows.get(cursor[0]).get((Integer) arguments[0] - 1);
            case "close" -> null;
            default -> fail("unexpected result method " + method.getName());
        });
    }

    private static List<List<String>> records(String data) {
        return data.lines().map(line -> List.of(line.split("\\|", -1))).toList();
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
