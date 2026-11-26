package com.campusflow.migrator;

import org.flywaydb.core.api.exception.FlywayValidateException;

import javax.net.ssl.SSLException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static com.campusflow.migrator.MigrationFailure.Category;

final class MigrationWorkflow {
    enum Check {
        TARGET, TLS, TABLES, OTHER_OBJECTS, COLUMNS, INDEXES, FOREIGN_KEYS, CHECK_CONSTRAINT,
        HISTORY, ROLE_SEEDS, MIGRATOR_GRANTS, APPLICATION_GRANTS, ROLE_INHERITANCE
    }

    enum Status { PASSED, MISMATCH, ADMIN_REQUIRED, UNAVAILABLE }

    record Finding(Check check, Status status) {}

    record Outcome(int applied, List<Finding> findings) {}

    @FunctionalInterface
    interface Action {
        Outcome migrate(char[] password, ApprovedMigrations resources) throws SQLException;
    }

    static int run(String[] args, Supplier<ApprovedMigrations> preflight, Supplier<char[]> credentials,
                   Supplier<String> confirmation, Action action, PrintStream output) {
        boolean checkOnly = args.length == 1 && "--check".equals(args[0]);
        if (args.length != 0 && !checkOnly) {
            return stop(output, Category.ARGUMENTS, false, null);
        }
        char[] password = null;
        boolean entered = false;
        try {
            ApprovedMigrations resources = preflight.get();
            output.printf("CampusFlow 测试迁移器 %s%n", MigrationConfiguration.VERSION);
            output.printf("目标 127.0.0.1:3306/%s，迁移账号 %s，目标版本 %s%n",
                    MigrationConfiguration.DATABASE, MigrationConfiguration.USER, MigrationConfiguration.TARGET_VERSION);
            for (var resource : resources.all()) {
                output.println(resource.getFilename());
            }
            if (checkOnly) {
                output.println("离线预检通过。");
                return 0;
            }

            output.println("请在测试组获得独立人工批准且管理员确认目标 schema 为空后输入以下完整确认文本。");
            output.println(MigrationConfiguration.CONFIRMATION);
            output.flush();
            if (!MigrationConfiguration.CONFIRMATION.equals(confirmation.get())) {
                return stop(output, Category.CONFIRMATION, false, null);
            }
            password = credentials.get();
            entered = true;
            Outcome result = action.migrate(password, resources);
            output.printf("Flyway 本次应用 %d 个版本。%n", result.applied());
            for (Finding finding : result.findings()) {
                output.printf("%s %s%n", finding.check(), finding.status());
            }
            long checked = result.findings().stream().map(Finding::check).distinct().count();
            long passed = result.findings().stream().filter(f -> f.status() == Status.PASSED)
                    .map(Finding::check).distinct().count();
            if (result.applied() != resources.all().size()
                    || result.findings().size() != Check.values().length || checked != Check.values().length
                    || result.findings().stream().anyMatch(f -> f.status() == Status.MISMATCH
                            || f.status() == Status.UNAVAILABLE)) {
                output.println("验核存在差异或不可用。停止后续操作，保留数据库现状。");
                return 5;
            }
            if (passed != Check.values().length) {
                output.println("迁移已返回成功，验核有待完成。请保留现场并核对第 7 节。");
                return 4;
            }
            output.println("迁移和只读验核通过。");
            return 0;
        } catch (RuntimeException | SQLException | LinkageError failure) {
            return stop(output, categoryOf(failure), entered, failure);
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private static int stop(PrintStream output, Category category, boolean entered, Throwable failure) {
        output.printf("操作停止 %s%n", category);
        if (entered) {
            MigrationDiagnostics.print(output, failure);
            output.println("请保留数据库现状并人工核对。修复或重跑须单独审批。");
        }
        return entered ? 3 : 2;
    }

    static Category categoryOf(Throwable failure) {
        Category category = Category.UNKNOWN;
        for (int depth = 0; depth < 16 && failure != null; depth++, failure = failure.getCause()) {
            if (failure instanceof MigrationFailure known) {
                return known.category();
            }
            if (failure instanceof SSLException) {
                return Category.TLS;
            }
            if (failure instanceof FlywayValidateException) {
                category = Category.HISTORY;
            } else if (failure instanceof SQLException sql) {
                category = switch (sql.getErrorCode()) {
                    case 1044, 1045, 1142, 1143, 1227, 1370 -> Category.PERMISSION;
                    case 2026 -> Category.TLS;
                    default -> sql.getSQLState() != null && sql.getSQLState().startsWith("08")
                            ? Category.CONNECTION
                            : (category == Category.UNKNOWN ? Category.SQL_EXECUTION : category);
                };
            }
        }
        return category;
    }

    private MigrationWorkflow() {}
}
