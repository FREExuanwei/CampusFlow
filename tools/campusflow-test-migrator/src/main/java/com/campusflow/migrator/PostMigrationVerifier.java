package com.campusflow.migrator;

import org.flywaydb.core.internal.resolver.ChecksumCalculator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.campusflow.migrator.MigrationDiagnostics.Operation;
import static com.campusflow.migrator.MigrationFailure.Category;
import static com.campusflow.migrator.MigrationWorkflow.Check;
import static com.campusflow.migrator.MigrationWorkflow.Finding;
import static com.campusflow.migrator.MigrationWorkflow.Status;

final class PostMigrationVerifier {
    private static final String MIGRATOR_ACCOUNT = "campusflow_example_migrator@127.0.0.1";
    private static final String SESSION = "SELECT DATABASE(), CURRENT_USER(), @@port";
    private static final String TLS = "SHOW SESSION STATUS LIKE 'Ssl_cipher'";
    private static final String MIGRATOR_GRANTS = "SHOW GRANTS FOR CURRENT_USER";
    private static final String CURRENT_ROLE = "SELECT CURRENT_ROLE()";
    private static final Map<String, Set<String>> EXPECTED_MIGRATOR_GRANTS = Map.of(
            "*.*", Set.of("USAGE"),
            "campusflow_test.users", Set.of("CREATE", "REFERENCES"),
            "campusflow_test.roles", Set.of("CREATE", "REFERENCES", "INSERT"),
            "campusflow_test.user_roles", Set.of("CREATE"),
            "campusflow_test.activities", Set.of("ALTER", "CREATE", "INSERT", "REFERENCES"),
            "campusflow_test.enrollments", Set.of("CREATE"),
            "campusflow_test.notifications", Set.of("CREATE"),
            "campusflow_test.operation_logs", Set.of("CREATE"),
            "campusflow_test.flyway_schema_history", Set.of("CREATE", "INDEX", "SELECT", "INSERT", "UPDATE"));
    private static final Set<String> CREATE_ONLY_TABLES = Set.of(
            "user_roles", "enrollments", "notifications", "operation_logs");
    private static final String TABLES = """
            SELECT TABLE_NAME, TABLE_TYPE, ENGINE
            FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'campusflow_test'
            """;
    private static final String OTHER_OBJECTS = """
            SELECT 'PRESENT' FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = 'campusflow_test'
            UNION ALL
            SELECT 'PRESENT' FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = 'campusflow_test'
            UNION ALL
            SELECT 'PRESENT' FROM information_schema.EVENTS WHERE EVENT_SCHEMA = 'campusflow_test'
            LIMIT 1
            """;
    private static final String CANDIDATE_LOCK = "SELECT GET_LOCK('campusflow_test_migrator_v6',0)";
    private static final String COLUMNS = """
            SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, EXTRA
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'campusflow_test'
            """;
    private static final String INDEXES = """
            SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, INDEX_TYPE,
                   COALESCE(CAST(SUB_PART AS CHAR), '')
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = 'campusflow_test'
            """;
    private static final String FOREIGN_KEYS = """
            SELECT k.TABLE_NAME, k.CONSTRAINT_NAME, k.COLUMN_NAME,
                   k.REFERENCED_TABLE_SCHEMA, k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME,
                   r.UPDATE_RULE, r.DELETE_RULE
            FROM information_schema.KEY_COLUMN_USAGE k
            JOIN information_schema.REFERENTIAL_CONSTRAINTS r
              ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
             AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME AND r.TABLE_NAME = k.TABLE_NAME
            WHERE k.TABLE_SCHEMA = 'campusflow_test'
              AND k.REFERENCED_TABLE_NAME IS NOT NULL
            """;
    private static final String CHECK_CONSTRAINTS = """
            SELECT t.TABLE_NAME, t.CONSTRAINT_NAME, t.ENFORCED, c.CHECK_CLAUSE
            FROM information_schema.TABLE_CONSTRAINTS t
            JOIN information_schema.CHECK_CONSTRAINTS c
              ON t.CONSTRAINT_SCHEMA = c.CONSTRAINT_SCHEMA AND t.CONSTRAINT_NAME = c.CONSTRAINT_NAME
            WHERE t.TABLE_SCHEMA = 'campusflow_test' AND t.CONSTRAINT_TYPE = 'CHECK'
            """;
    private static final String HISTORY = """
            SELECT installed_rank, version, script, type, checksum,
                   CASE WHEN success = 1 THEN 'PASS' ELSE 'FAIL' END
            FROM campusflow_test.flyway_schema_history ORDER BY installed_rank
            """;
    private static final List<List<String>> EXPECTED_COLUMNS = records("""
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
            """);
    private static final List<List<String>> EXPECTED_INDEXES = records("""
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
            """);
    private static final List<List<String>> EXPECTED_FOREIGN_KEYS = records("""
            user_roles|fk_user_roles_user|user_id|campusflow_test|users|id
            user_roles|fk_user_roles_role|role_id|campusflow_test|roles|id
            activities|fk_activities_creator|created_by|campusflow_test|users|id
            activities|fk_activities_reviewer|reviewed_by|campusflow_test|users|id
            enrollments|fk_enrollments_activity|activity_id|campusflow_test|activities|id
            enrollments|fk_enrollments_user|user_id|campusflow_test|users|id
            notifications|fk_notifications_user|user_id|campusflow_test|users|id
            notifications|fk_notifications_activity|activity_id|campusflow_test|activities|id
            operation_logs|fk_operation_logs_actor|actor_id|campusflow_test|users|id
            """);
    private static final List<List<String>> EXPECTED_CHECKS = records("""
            users|ck_users_status|YES|CASESTATUSWHEN'ACTIVE'THEN1WHEN'DISABLED'THEN1ELSE0END=1
            activities|ck_activities_schedule|YES|ENDS_AT>STARTS_AT
            activities|ck_activities_capacity|YES|CAPACITY>0
            activities|ck_activities_status|YES|CASESTATUSWHEN'DRAFT'THEN1WHEN'SUBMITTED'THEN1WHEN'APPROVED'THEN1WHEN'REJECTED'THEN1WHEN'PUBLISHED'THEN1ELSE0END=1
            enrollments|ck_enrollments_status|YES|CASESTATUSWHEN'ENROLLED'THENCASEWHENCANCELLED_ATISNULLTHEN1ELSE0ENDWHEN'CANCELLED'THENCASEWHENCANCELLED_ATISNOTNULLTHEN1ELSE0ENDELSE0END=1
            """);

    private record Inspection(Check check, StatusCheck verify) {}

    @FunctionalInterface
    private interface QueryCheck { boolean run() throws SQLException; }

    @FunctionalInterface
    private interface StatusCheck { Status run() throws SQLException; }

    private static Inspection exact(Check check, QueryCheck comparison) {
        return new Inspection(check, () -> comparison.run() ? Status.PASSED : Status.MISMATCH);
    }

    static void requireMigrationSession(Connection connection) throws SQLException {
        requireSessionCheck(() -> targetMatches(connection), Category.CONFIGURATION, Operation.SESSION_TARGET);
        requireSessionCheck(() -> tlsActive(connection), Category.TLS, Operation.SESSION_TLS);
        requireSessionCheck(() -> rows(connection, CURRENT_ROLE, 1).equals(List.of(List.of("NONE"))),
                Category.PERMISSION, Operation.SESSION_CURRENT_ROLE);
        requireSessionCheck(() -> grantsMatch(connection, MIGRATOR_GRANTS, MIGRATOR_ACCOUNT, EXPECTED_MIGRATOR_GRANTS),
                Category.PERMISSION, Operation.SESSION_MIGRATOR_GRANTS);
    }

    static void requireCandidateLock(Connection connection) throws SQLException {
        requireSessionCheck(() -> rows(connection, CANDIDATE_LOCK, 1).equals(List.of(List.of("1"))),
                Category.LOCK, Operation.SESSION_CANDIDATE_LOCK);
    }

    static void requireEmptySchema(Connection connection) throws SQLException {
        requireSessionCheck(() -> rows(connection, TABLES, 3).isEmpty()
                        && rows(connection, OTHER_OBJECTS, 1).isEmpty(),
                Category.HISTORY, Operation.SESSION_EMPTY_SCHEMA);
    }

    private static void requireSessionCheck(QueryCheck check, Category category, Operation operation) throws SQLException {
        try {
            if (!check.run()) {
                throw new MigrationFailure(category);
            }
        } catch (SQLException | RuntimeException failure) {
            MigrationDiagnostics.mark(failure, operation);
            throw failure;
        }
    }

    static List<Finding> inspect(Connection connection, ApprovedMigrations migrations) {
        List<Inspection> inspections = List.of(
                exact(Check.TARGET, () -> targetMatches(connection)),
                exact(Check.TLS, () -> tlsActive(connection)),
                exact(Check.TABLES, () -> tablesMatch(connection)),
                exact(Check.OTHER_OBJECTS, () -> rows(connection, OTHER_OBJECTS, 1).isEmpty()),
                new Inspection(Check.COLUMNS, () -> compareCreateOnlyMetadata(rows(connection, COLUMNS, 5),
                        EXPECTED_COLUMNS)),
                exact(Check.INDEXES, () -> sameRows(rows(connection, INDEXES, 7), EXPECTED_INDEXES)),
                new Inspection(Check.FOREIGN_KEYS, () -> foreignKeysStatus(connection)),
                exact(Check.CHECK_CONSTRAINT, () -> checkConstraintMatches(connection)),
                exact(Check.HISTORY, () -> historyMatches(connection, migrations)),
                exact(Check.MIGRATOR_GRANTS,
                        () -> grantsMatch(connection, MIGRATOR_GRANTS, MIGRATOR_ACCOUNT, EXPECTED_MIGRATOR_GRANTS)));
        List<Finding> findings = new ArrayList<>();
        for (Inspection inspection : inspections) {
            Status status;
            try {
                status = inspection.verify().run();
            } catch (SQLException e) {
                status = MigrationWorkflow.categoryOf(e) == Category.PERMISSION ? Status.ADMIN_REQUIRED : Status.UNAVAILABLE;
            }
            findings.add(new Finding(inspection.check(), status));
            boolean unsafeSession = status != Status.PASSED
                    && (inspection.check() == Check.TARGET || inspection.check() == Check.TLS);
            if (status == Status.UNAVAILABLE || unsafeSession) {
                return List.copyOf(findings);
            }
        }
        // These remain DBA checks under the signed grant set.
        findings.add(new Finding(Check.ROLE_SEEDS, Status.ADMIN_REQUIRED));
        findings.add(new Finding(Check.APPLICATION_GRANTS, Status.ADMIN_REQUIRED));
        findings.add(new Finding(Check.ROLE_INHERITANCE, Status.ADMIN_REQUIRED));
        return List.copyOf(findings);
    }

    private static boolean targetMatches(Connection connection) throws SQLException {
        return rows(connection, SESSION, 3)
                .equals(List.of(List.of(MigrationConfiguration.DATABASE, MIGRATOR_ACCOUNT, "3306")));
    }

    private static boolean tlsActive(Connection connection) throws SQLException {
        var result = rows(connection, TLS, 2);
        return result.size() == 1 && "Ssl_cipher".equalsIgnoreCase(result.getFirst().getFirst())
                && result.getFirst().get(1) != null && !result.getFirst().get(1).isBlank();
    }

    private static boolean tablesMatch(Connection connection) throws SQLException {
        var actual = rows(connection, TABLES, 3);
        var expected = List.of("users", "roles", "user_roles", "activities", "enrollments",
                        "notifications", "operation_logs", "flyway_schema_history").stream()
                .map(name -> List.of(name, "BASE TABLE", "InnoDB")).toList();
        return sameRows(actual, expected);
    }

    private static Status compareCreateOnlyMetadata(List<List<String>> actual, List<List<String>> expected) {
        if (sameRows(actual, expected)) {
            return Status.PASSED;
        }
        // CREATE alone exposes table/index names in MySQL 9.7, but not all column or FK metadata.
        var usuallyVisible = expected.stream().filter(row -> !CREATE_ONLY_TABLES.contains(row.getFirst())).toList();
        return expected.containsAll(actual) && actual.containsAll(usuallyVisible)
                && new HashSet<>(actual).size() == actual.size() ? Status.ADMIN_REQUIRED : Status.MISMATCH;
    }

    private static Status foreignKeysStatus(Connection connection) throws SQLException {
        List<List<String>> actualKeys = new ArrayList<>();
        for (var row : rows(connection, FOREIGN_KEYS, 8)) {
            if (!restrictive(row.get(6)) || !restrictive(row.get(7))) {
                return Status.MISMATCH;
            }
            actualKeys.add(row.subList(0, 6));
        }
        return compareCreateOnlyMetadata(actualKeys, EXPECTED_FOREIGN_KEYS);
    }

    private static boolean restrictive(String rule) {
        return "RESTRICT".equals(rule) || "NO ACTION".equals(rule);
    }

    private static boolean checkConstraintMatches(Connection connection) throws SQLException {
        var checks = new ArrayList<List<String>>();
        for (var row : rows(connection, CHECK_CONSTRAINTS, 4)) {
            if (row.get(3) == null) {
                return false;
            }
            checks.add(List.of(row.get(0), row.get(1), row.get(2), checkTokens(row.get(3))));
        }
        return sameRows(checks, EXPECTED_CHECKS);
    }

    private static String checkTokens(String clause) {
        // MySQL can add parentheses, quoted identifiers and charset introducers.
        String normalized = clause.replaceAll("(?i)_utf8mb4\\\\'([A-Z_]+)\\\\'", "'$1'");
        String[] parts = normalized.split("'", -1);
        for (int index = 0; index < parts.length; index += 2) {
            parts[index] = parts[index].replaceAll("(?i)_utf8mb4\\s*$", "")
                    .replaceAll("[\\s()`]", "").toUpperCase(Locale.ROOT);
        }
        return String.join("'", parts);
    }

    private static boolean historyMatches(Connection connection, ApprovedMigrations migrations) throws SQLException {
        List<List<String>> expected = new ArrayList<>();
        int rank = 0;
        for (var resource : migrations.all()) {
            String version = Integer.toString(++rank);
            expected.add(List.of(version, version, resource.getFilename(), "SQL",
                    Integer.toString(ChecksumCalculator.calculate(resource)), "PASS"));
        }
        return rows(connection, HISTORY, 6).equals(expected);
    }

    private static boolean grantsMatch(Connection connection, String sql, String account,
                                       Map<String, Set<String>> expected) throws SQLException {
        Map<String, Set<String>> actual = new HashMap<>();
        for (var row : rows(connection, sql, 1)) {
            String grant = row.getFirst().replace("`", "").replace("'", "");
            int on = grant.indexOf(" ON ");
            int to = grant.indexOf(" TO ");
            if (!grant.startsWith("GRANT ") || on < 6 || to <= on || !grant.substring(to + 4).equals(account)) {
                return false;
            }
            String object = grant.substring(on + 4, to);
            Set<String> privileges = Arrays.stream(grant.substring(6, on).split(","))
                    .map(String::trim).collect(Collectors.toSet());
            if (actual.putIfAbsent(object, privileges) != null) {
                return false;
            }
        }
        return actual.equals(expected);
    }

    private static List<List<String>> records(String text) {
        return text.lines().map(line -> List.of(line.split("\\|", -1))).toList();
    }

    private static boolean sameRows(List<List<String>> actual, List<List<String>> expected) {
        return actual.size() == expected.size() && new HashSet<>(actual).equals(new HashSet<>(expected));
    }

    private static List<List<String>> rows(Connection connection, String sql, int columns) throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        try (var statement = connection.createStatement(); var results = statement.executeQuery(sql)) {
            while (results.next()) {
                var row = new ArrayList<String>(columns);
                for (int column = 1; column <= columns; column++) {
                    row.add(results.getString(column));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private PostMigrationVerifier() {}
}
