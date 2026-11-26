package com.campusflow.common.config;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlTestRuntimeTests {

    private static final String FAKE_PASSWORD = "fixture '${ignored.placeholder}'";
    private static final String FAKE_SIGNING_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @TempDir
    private Path root;

    @Test
    void startsTheIsolatedProbeWithTheFixedDataSourceAndSafetySettings() throws Exception {
        writeCredentials();
        int exitCode = runProbe();
        var guardFrames = Files.readAllLines(root.resolve("probe.log"), StandardCharsets.UTF_8).stream()
                .map(String::strip).filter(line -> line.startsWith("at com.campusflow.common.config.MysqlTestRuntime."))
                .toList();
        assertThat(exitCode).as("probe exit with guard stack %s", guardFrames).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "--spring.datasource.url=jdbc:mysql://127.0.0.1:1/campusflow_db",
            "--spring.datasource.hikari.data-source-properties.user=root",
            "--spring.flyway.enabled=true",
            "--spring.sql.init.mode=always",
            "--spring.jpa.properties.hibernate.hbm2ddl.auto=update",
            "--spring.jpa.properties.hibernate.default_catalog=campusflow_db",
            "--spring.autoconfigure.exclude=org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "--server.address=0.0.0.0",
            "--server.port=8080",
            "--spring.profiles.include=persistence-test",
            "--campusflow.auth.signing-key-base64=override"
    })
    void rejectsUnsafeOverridesBeforeContextRefresh(String override) throws Exception {
        writeCredentials();
        assertThat(runProbe(override)).isEqualTo(12);
    }

    @Test
    void rejectsMissingCredentials() throws Exception {
        assertThat(runProbe()).isEqualTo(13);
    }

    @Test
    void rejectsAConfigImportBeforeSpringAttemptsToReadIt() throws Exception {
        writeCredentials();
        assertThat(runProbe("--spring.config.import=file:./missing-import.yml")).isEqualTo(12);
    }

    @Test
    void doesNotLoadDefaultFilesystemSpringYaml() throws Exception {
        writeCredentials();
        Files.writeString(root.resolve("application.yml"), "server: [unclosed-list");
        assertThat(runProbe()).isZero();
    }

    @Test
    void rejectsMalformedCredentialsWithoutShowingTheYaml() throws Exception {
        Files.writeString(root.resolve("application-local.yml"), "campusflow: [unterminated-test-marker");
        assertThat(runProbe()).isEqualTo(13);
        assertThat(Files.readString(root.resolve("probe.log"))).doesNotContain("unterminated-test-marker");
    }

    private void writeCredentials() throws Exception {
        Files.writeString(root.resolve("application-local.yml"), """
                campusflow:
                  local:
                    database:
                      test-app-password: "fixture '${ignored.placeholder}'"
                    jwt:
                      test-signing-key-base64: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
                spring:
                  datasource:
                    username: root
                  flyway:
                    enabled: true
                server:
                  address: "0.0.0.0"
                """, StandardCharsets.UTF_8);
    }

    private int runProbe(String... overrides) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        var command = new ArrayList<>(List.of(java.toString(), "-cp", System.getProperty("java.class.path"),
                Probe.class.getName(), "--spring.profiles.active=mysql-test"));
        command.addAll(List.of(overrides));
        var process = new ProcessBuilder(command).directory(root.toFile())
                .redirectErrorStream(true).redirectOutput(root.resolve("probe.log").toFile()).start();
        try {
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
            return process.exitValue();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    public static final class Probe {

        private Probe() {
        }

        public static void main(String[] args) {
            try (var context = new SpringApplicationBuilder(Probe.class).web(WebApplicationType.NONE).run(args)) {
                var source = context.getBean(HikariDataSource.class);
                assertThat(source.getJdbcUrl()).isEqualTo("jdbc:mysql://127.0.0.1:3306/campusflow_test"
                        + "?sslMode=REQUIRED&allowPublicKeyRetrieval=false&connectTimeout=3000&socketTimeout=10000"
                        + "&connectionTimeZone=%2B00:00&forceConnectionTimeZoneToSession=true&autoReconnect=false");
                assertThat(source.getUsername()).isEqualTo("campusflow_example_app");
                assertThat(source.getPassword()).isEqualTo(FAKE_PASSWORD);
                assertThat(source.getMaximumPoolSize()).isEqualTo(5);
                assertThat(source.getMinimumIdle()).isZero();
                var environment = context.getEnvironment();
                assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
                assertThat(environment.getProperty("server.port")).isEqualTo("18086");
                assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("false");
                assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
                assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
                assertThat(environment.getProperty("campusflow.auth.signing-key-base64")).isEqualTo(FAKE_SIGNING_KEY);
                assertThat(environment.getProperty("campusflow.local.database.test-app-password")).isNull();
            } catch (IllegalStateException failure) {
                if ("mysql-test startup rejected an unsafe configuration override.".equals(failure.getMessage())) {
                    System.exit(12);
                }
                if ("mysql-test requires its application password and Base64 signing key in application-local.yml."
                        .equals(failure.getMessage())) {
                    System.exit(13);
                }
                throw failure;
            }
        }
    }
}
