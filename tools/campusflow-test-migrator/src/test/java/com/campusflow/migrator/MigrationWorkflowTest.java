package com.campusflow.migrator;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class MigrationWorkflowTest {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final AtomicInteger calls = new AtomicInteger();

    @Test void absentConfirmationExitsBeforeReadingTheCredential() {
        assertEquals(2, run(new String[0], () -> null, this::unexpectedCredential, this::succeed));
        assertEquals(0, calls.get());
    }

    @Test void confirmationMustMatchExactly() {
        for (String input : List.of("", "migrate campusflow_test V1-V6", "MIGRATE campusflow_test V1-V6 ")) {
            assertEquals(2, run(new String[0], () -> input, this::unexpectedCredential, this::succeed));
        }
        assertEquals(0, calls.get());
    }

    @Test void correctConfirmationEntersOnlyTheInjectedActionAndClearsThePassword() {
        char[] fake = "fixture-credential".toCharArray();
        assertEquals(0, run(new String[0], this::confirm, () -> fake, this::succeed));
        assertArrayEquals(new char[fake.length], fake);
        assertEquals(1, calls.get());
        assertTrue(messages().contains("campusflow_test"));
        assertTrue(messages().contains("0.2.0"));
        assertTrue(messages().contains("V1__create_user_tables.sql"));
        assertTrue(messages().contains("V6__notifications_and_operation_logs.sql"));
        assertFalse(messages().contains("fixture-credential"));
    }

    @Test void failedExecutionReportsSafeDiagnosticsAndDoesNotRetry() {
        int code = run(new String[0], this::confirm, () -> "test-only".toCharArray(), (password, files) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("test-only wrapper diagnostic",
                    new SQLException("test-only raw diagnostic", "42000", 1142));
        });
        assertEquals(3, code);
        assertEquals(1, calls.get());
        assertTrue(messages().lines().anyMatch("操作停止 PERMISSION"::equals));
        assertTrue(messages().lines().anyMatch(
                "诊断 stage=EXECUTION operation=MIGRATION_ACTION jdbc_category=SQL_EXCEPTION mysql_errno=1142 sqlstate=42000"::equals));
        assertFalse(messages().contains("test-only"));
    }

    @Test void aSqlStateOutsideTheAllowlistIsOmittedEvenWhenItsFormatLooksValid() {
        assertEquals(3, run(new String[0], this::confirm, () -> "test-only".toCharArray(), (password, files) -> {
            throw new SQLException("test-only raw diagnostic", "ZZZZZ", 1142);
        }));
        assertTrue(messages().lines().anyMatch(
                "诊断 stage=EXECUTION operation=MIGRATION_ACTION jdbc_category=SQL_EXCEPTION mysql_errno=1142"::equals));
        assertFalse(messages().contains("ZZZZZ"));
    }

    @Test void successfulExecutionWithIncompleteVerificationHasADistinctExit() {
        int code = run(new String[0], this::confirm, () -> "test-only".toCharArray(),
                (password, files) -> new MigrationWorkflow.Outcome(6,
                        Arrays.stream(MigrationWorkflow.Check.values())
                                .map(check -> new MigrationWorkflow.Finding(check,
                                        check == MigrationWorkflow.Check.APPLICATION_GRANTS
                                                ? MigrationWorkflow.Status.ADMIN_REQUIRED : MigrationWorkflow.Status.PASSED))
                                .toList()));
        assertEquals(4, code);
        assertTrue(messages().contains("APPLICATION_GRANTS"));
        assertTrue(messages().contains("ADMIN_REQUIRED"));
    }

    @Test void aPassedHistoryCheckIsInsufficientToClaimAllChecksPassed() {
        assertEquals(5, run(new String[0], this::confirm, () -> "test-only".toCharArray(),
                (password, files) -> new MigrationWorkflow.Outcome(6, List.of(new MigrationWorkflow.Finding(
                        MigrationWorkflow.Check.HISTORY, MigrationWorkflow.Status.PASSED)))));
    }

    @Test void anyCommandLineOverrideIsRejectedBeforePreflight() {
        var result = MigrationWorkflow.run(new String[]{"--url=unapproved", "unsafe-fixture"},
                () -> fail("preflight must not run"), this::unexpectedCredential,
                () -> fail("confirmation must not run"), this::succeed, stream());
        assertEquals(2, result);
        assertFalse(messages().contains("unsafe-fixture"));
        assertEquals(0, calls.get());
    }

    @Test void offlineCheckReadsNeitherConfirmationNorSecrets() {
        assertEquals(0, run(new String[]{"--check"}, () -> fail("confirmation must not run"),
                this::unexpectedCredential, this::succeed));
        assertEquals(0, calls.get());
    }

    @Test void missingCredentialStopsEvenAfterCorrectConfirmation() {
        assertEquals(2, run(new String[0], this::confirm, () -> {
            throw new MigrationFailure(MigrationFailure.Category.CREDENTIAL);
        }, this::succeed));
        assertTrue(messages().contains("CREDENTIAL"));
        assertEquals(0, calls.get());
    }

    private int run(String[] args, Supplier<String> confirmation, Supplier<char[]> credential,
                    MigrationWorkflow.Action action) {
        return MigrationWorkflow.run(args, () -> ApprovedMigrations.load(getClass().getClassLoader()),
                credential, confirmation, action, stream());
    }

    private char[] unexpectedCredential() {
        return fail("credential must not be read");
    }

    private String confirm() {
        return "MIGRATE campusflow_test V1-V6";
    }

    private MigrationWorkflow.Outcome succeed(char[] password, ApprovedMigrations resources) {
        calls.incrementAndGet();
        assertTrue(password.length > 0);
        assertEquals(6, resources.all().size());
        var findings = java.util.Arrays.stream(MigrationWorkflow.Check.values())
                .map(check -> new MigrationWorkflow.Finding(check, MigrationWorkflow.Status.PASSED)).toList();
        return new MigrationWorkflow.Outcome(6, findings);
    }

    private PrintStream stream() {
        return new PrintStream(output, true, StandardCharsets.UTF_8);
    }

    private String messages() {
        return output.toString(StandardCharsets.UTF_8);
    }
}
