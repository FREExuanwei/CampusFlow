package com.campusflow.migrator;

import java.io.Console;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Supplier;

public final class TestMigratorApplication {
    public static void main(String[] args) {
        PrintStream output = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> output.println("操作停止 UNKNOWN"));

        Supplier<ApprovedMigrations> preflight = () -> {
            ReleaseClasspath.verifyInvocation();
            ApprovedMigrations resources = ApprovedMigrations.load(TestMigratorApplication.class.getClassLoader());
            var configuration = MigrationConfiguration.fixed("offline-placeholder".toCharArray(), resources);
            MigrationConfiguration.verify(configuration, resources);
            MigrationConfiguration.verify(configuration.load().getConfiguration(), resources);
            return resources;
        };
        int exit = MigrationWorkflow.run(args, preflight, TestMigratorApplication::readCredential,
                TestMigratorApplication::confirm, MigrationRunner::migrate, output);
        System.exit(exit);
    }

    private static char[] readCredential() {
        Path projectRoot = ReleaseClasspath.projectRoot();
        return new CredentialLoader(projectRoot).load();
    }

    private static String confirm() {
        Console console = System.console();
        return console == null || !console.isTerminal() ? null : console.readLine();
    }

    private TestMigratorApplication() {}
}
