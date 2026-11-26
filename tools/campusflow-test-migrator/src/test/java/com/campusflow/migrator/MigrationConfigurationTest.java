package com.campusflow.migrator;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MigrationConfigurationTest {
    @Test void locksTheMysqlTargetAndAccountWithoutAConnection() {
        var c = configuration();
        assertEquals("jdbc:mysql://127.0.0.1:3306/campusflow_test?sslMode=REQUIRED&allowPublicKeyRetrieval=false&connectTimeout=3000&socketTimeout=10000&connectionTimeZone=%2B00:00&forceConnectionTimeZoneToSession=true&autoReconnect=false&logger=com.mysql.cj.log.NullLogger", c.getUrl());
        assertEquals("campusflow_example_migrator", c.getUser());
        assertEquals("campusflow_test", c.getDefaultSchema());
        assertArrayEquals(new String[]{"campusflow_test"}, c.getSchemas());
        assertEquals("6", c.getTarget().toString());
        assertEquals("classpath:db/migration", c.getLocations()[0].getDescriptor());
    }

    @Test void disablesDestructiveAndOutOfOrderPaths() {
        var c = configuration();
        assertTrue(c.isCleanDisabled());
        assertFalse(c.isCreateSchemas());
        assertFalse(c.isBaselineOnMigrate());
        assertFalse(c.isOutOfOrder());
        assertTrue(c.isValidateOnMigrate());
        assertTrue(c.isValidateMigrationNaming());
    }

    @Test void removesExtraSqlCallbacksAndJavaMigrations() {
        var c = configuration();
        assertNull(c.getInitSql());
        assertEquals(0, c.getCallbackLocations().length);
        assertEquals(0, c.getCallbacks().length);
        assertEquals(0, c.getJavaMigrations().length);
        assertTrue(c.getJavaMigrationClassProvider().getClasses().isEmpty());
        assertTrue(c.isSkipDefaultCallbacks());
        assertEquals(0, c.getResolvers().length);
        assertFalse(c.isPlaceholderReplacement());
    }

    @Test void genericFlywayConnectionAndLockRetryCountsAreZero() {
        var c = configuration();
        assertEquals(0, c.getConnectRetries());
        assertEquals(0, c.getLockRetryCount());
    }

    @Test void jvmPropertiesCannotChangeTargetOrPassword() {
        String old = System.getProperty("flyway.url");
        System.setProperty("flyway.url", "jdbc:mysql://127.0.0.1:1/campusflow_db");
        try {
            var c = configuration();
            assertTrue(c.getUrl().startsWith("jdbc:mysql://127.0.0.1:3306/campusflow_test?"));
            assertEquals("fictional-only", ((FixedMysqlDataSource) c.getDataSource()).getPassword());
        } finally {
            if (old == null) System.clearProperty("flyway.url");
            else System.setProperty("flyway.url", old);
        }
    }

    @Test void flywayKeepsTheFixedConnectorDataSourceAndLiteralCredentialWhenItLoads() {
        var resources = ApprovedMigrations.load(getClass().getClassLoader());
        for (String fake : new String[]{"  \t", "quoted-${environment:fictional}#' \\"}) {
            var configuration = MigrationConfiguration.fixed(fake.toCharArray(), resources);
            var loaded = configuration.load().getConfiguration();
            MigrationConfiguration.verify(loaded, resources);
            assertSame(configuration.getDataSource(), loaded.getDataSource());
            assertEquals(fake, ((FixedMysqlDataSource) loaded.getDataSource()).getPassword());
        }
    }

    @Test void onlyApprovedResourcesAreOfferedToTheResolver() {
        var r = ApprovedMigrations.load(getClass().getClassLoader());
        assertEquals(6, r.all().size());
        assertEquals(6, r.getResources("V", new String[]{".sql"}).size());
        assertTrue(r.getResources("R", new String[]{".sql"}).isEmpty());
        assertTrue(r.getResources("before", new String[]{".sql"}).isEmpty());
        assertNull(r.getResource("../../application-local.yml"));
    }

    @Test void additionalMigrationIsRejected() throws Exception {
        var contents = contents();
        contents.put("V7__unexpected.sql", "synthetic-extra".getBytes(StandardCharsets.UTF_8));
        assertThrows(MigrationFailure.class, () -> ApprovedMigrations.verify(contents));
    }

    @Test void missingMigrationIsRejected() throws Exception {
        var contents = contents();
        contents.remove("V6__notifications_and_operation_logs.sql");
        assertThrows(MigrationFailure.class, () -> ApprovedMigrations.verify(contents));
    }

    @Test void modifiedMigrationIsRejected() throws Exception {
        var contents = contents();
        for (byte[] bytes : contents.values()) {
            bytes[0] ^= 1;
            assertThrows(MigrationFailure.class, () -> ApprovedMigrations.verify(contents));
            bytes[0] ^= 1;
        }
    }

    @Test void packagedBytesMatchEveryReviewedSourceMigration() throws Exception {
        var directory = java.nio.file.Path.of("..", "..", "src", "main", "resources", "db", "migration");
        var contents = contents();
        assertEquals(6, contents.size());
        for (var entry : contents.entrySet()) {
            assertArrayEquals(java.nio.file.Files.readAllBytes(directory.resolve(entry.getKey())), entry.getValue());
        }
        assertEquals(6, ApprovedMigrations.verify(contents).all().size());
    }

    @Test void distinguishesAnUnapprovedEffectiveConfiguration() {
        var files = ApprovedMigrations.load(getClass().getClassLoader());
        var withOlderTarget = MigrationConfiguration.fixed("fixture".toCharArray(), files).target("2");
        assertThrows(MigrationFailure.class, () -> MigrationConfiguration.verify(withOlderTarget, files));
        var withInitSql = MigrationConfiguration.fixed("fixture".toCharArray(), files).initSql("synthetic-forbidden-init");
        assertThrows(MigrationFailure.class, () -> MigrationConfiguration.verify(withInitSql, files));
    }

    @Test void fixedConfigurationDoesNotLogTheFictionalPassword() {
        var buffer = new ByteArrayOutputStream();
        PrintStream before = System.out;
        try {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            configuration();
        } finally { System.setOut(before); }
        assertFalse(buffer.toString(StandardCharsets.UTF_8).contains("fictional-only"));
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration configuration() {
        return MigrationConfiguration.fixed("fictional-only".toCharArray(),
                ApprovedMigrations.load(getClass().getClassLoader()));
    }

    private Map<String, byte[]> contents() throws Exception {
        Map<String, byte[]> result = new HashMap<>();
        for (String name : new String[]{
                "V1__create_user_tables.sql", "V2__seed_base_roles.sql", "V3__create_activities.sql",
                "V4__activity_review_and_publication.sql", "V5__create_enrollments.sql",
                "V6__notifications_and_operation_logs.sql"}) {
            try (var in = getClass().getClassLoader().getResourceAsStream("db/migration/" + name)) {
                result.put(name, in.readAllBytes());
            }
        }
        return result;
    }
}
