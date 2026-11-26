package com.campusflow.migrator;

import org.flywaydb.core.api.output.MigrateResult;

import java.sql.SQLException;
import java.util.List;

import static com.campusflow.migrator.MigrationDiagnostics.Operation.FLYWAY_MIGRATE;

final class MigrationRunner {
    static MigrationWorkflow.Outcome migrate(char[] password, ApprovedMigrations resources) throws SQLException {
        var configuration = MigrationConfiguration.fixed(password, resources);
        MigrationConfiguration.verify(configuration, resources);
        var flyway = configuration.load();
        MigrationConfiguration.verify(flyway.getConfiguration(), resources);
        var dataSource = flyway.getConfiguration().getDataSource();

        MigrateResult result = null;
        try (var connection = dataSource.getConnection()) {
            // Hold the candidate's named lock through the empty check, migration and inspection.
            PostMigrationVerifier.requireCandidateLock(connection);
            PostMigrationVerifier.requireEmptySchema(connection);
            result = applyOnce(flyway, resources);
            return new MigrationWorkflow.Outcome(result.migrationsExecuted,
                    PostMigrationVerifier.inspect(connection, resources));
        } catch (SQLException | RuntimeException e) {
            if (result == null) {
                throw e;
            }
            return new MigrationWorkflow.Outcome(result.migrationsExecuted, List.of(new MigrationWorkflow.Finding(
                    MigrationWorkflow.Check.TARGET, MigrationWorkflow.Status.UNAVAILABLE)));
        }
    }

    private static MigrateResult applyOnce(org.flywaydb.core.Flyway flyway, ApprovedMigrations resources) {
        try {
            MigrateResult result = flyway.migrate();
            if (!result.success || result.migrationsExecuted != resources.all().size()) {
                throw new MigrationFailure(MigrationFailure.Category.HISTORY);
            }
            return result;
        } catch (RuntimeException | LinkageError failure) {
            MigrationDiagnostics.mark(failure, FLYWAY_MIGRATE);
            throw failure;
        }
    }

    private MigrationRunner() {}
}
