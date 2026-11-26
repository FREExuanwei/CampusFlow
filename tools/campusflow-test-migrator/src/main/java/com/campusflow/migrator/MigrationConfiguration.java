package com.campusflow.migrator;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.resolver.MigrationResolver;

import java.util.Arrays;
import java.util.List;

final class MigrationConfiguration {
    static final String DATABASE = "campusflow_test";
    static final String USER = "campusflow_example_migrator";
    static final String URL = "jdbc:mysql://127.0.0.1:3306/campusflow_test"
            + "?sslMode=REQUIRED&allowPublicKeyRetrieval=false&connectTimeout=3000&socketTimeout=10000"
            + "&connectionTimeZone=%2B00:00&forceConnectionTimeZoneToSession=true"
            + "&autoReconnect=false&logger=com.mysql.cj.log.NullLogger";
    static final String TARGET_VERSION = "6";
    static final String CONFIRMATION = "MIGRATE campusflow_test V1-V6";
    static final String VERSION = "0.2.0";

    static FluentConfiguration fixed(char[] password, ApprovedMigrations resources) {
        ClassLoader loader = MigrationConfiguration.class.getClassLoader();
        var fixed = new ClassicConfiguration(loader);
        fixed.setUrl(URL);
        fixed.setUser(USER);
        fixed.setDataSource(new FixedMysqlDataSource(password));
        return new FluentConfiguration(loader)
                .configuration(fixed)
                .loggers(SilentFlywayLogger.class.getName())
                .driver("com.mysql.cj.jdbc.Driver")
                .defaultSchema(DATABASE).schemas(DATABASE)
                .locations("classpath:db/migration").target(TARGET_VERSION).table("flyway_schema_history")
                .createSchemas(false).cleanDisabled(true).baselineOnMigrate(false)
                .validateOnMigrate(true).validateMigrationNaming(true)
                .ignoreMigrationPatterns(new String[0]).outOfOrder(false)
                .connectRetries(0).lockRetryCount(0).initSql(null)
                .callbackLocations(new String[0]).callbacks(new Callback[0]).skipDefaultCallbacks(true)
                .javaMigrations(new JavaMigration[0]).javaMigrationClassProvider(java.util.List::of)
                .resolvers(new MigrationResolver[0]).skipDefaultResolvers(false)
                .resourceProvider(resources).skipExecutingMigrations(false)
                .placeholderReplacement(false).outputQueryResults(false);
    }

    static void verify(Configuration config, ApprovedMigrations resources) {
        boolean fixedDestination = URL.equals(config.getUrl()) && USER.equals(config.getUser())
                && DATABASE.equals(config.getDefaultSchema())
                && Arrays.equals(new String[]{DATABASE}, config.getSchemas())
                && TARGET_VERSION.equals(config.getTarget().toString())
                && List.of("classpath:db/migration").equals(Arrays.stream(config.getLocations())
                    .map(location -> location.getDescriptor()).toList())
                && "flyway_schema_history".equals(config.getTable())
                && config.getDataSource() instanceof FixedMysqlDataSource source
                && URL.equals(source.getUrl()) && USER.equals(source.getUser());
        boolean safeExecution = config.isCleanDisabled() && !config.isBaselineOnMigrate()
                && config.isValidateOnMigrate() && config.isValidateMigrationNaming()
                && !config.isOutOfOrder() && !config.isCreateSchemas()
                && !config.isSkipExecutingMigrations() && !config.isSkipDefaultResolvers()
                && config.getIgnoreMigrationPatterns().length == 0
                && config.getConnectRetries() == 0 && config.getLockRetryCount() == 0
                && !config.isReportEnabled() && !config.isOutputQueryResults()
                && Arrays.equals(new String[]{SilentFlywayLogger.class.getName()}, config.getLoggers());
        boolean onlyApprovedResources = config.getResourceProvider() == resources
                && config.getInitSql() == null && config.getCallbacks().length == 0
                && config.getCallbackLocations().length == 0 && config.isSkipDefaultCallbacks()
                && config.getJavaMigrations().length == 0 && config.getJavaMigrationClassProvider().getClasses().isEmpty()
                && config.getResolvers().length == 0 && !config.isPlaceholderReplacement();
        if (!fixedDestination || !safeExecution || !onlyApprovedResources) {
            throw new MigrationFailure(MigrationFailure.Category.CONFIGURATION);
        }
    }

    private MigrationConfiguration() {
    }
}
