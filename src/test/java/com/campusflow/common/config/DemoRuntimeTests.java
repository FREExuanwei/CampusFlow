package com.campusflow.common.config;

import com.campusflow.CampusFlowApplication;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.GenericWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemoRuntimeTests {
    @TempDir
    private Path root;

    @Test
    void createsOnlyTheProjectLocalH2DataSourceWithoutLoadingFilesystemConfiguration() throws Exception {
        writeCredentials();
        Files.writeString(root.resolve("application.yml"), "spring: [invalid-config-that-must-not-be-read");
        Files.writeString(root.resolve("application-local.yml"), "local: [invalid-config-that-must-not-be-read");
        assertThat(runProbe()).isZero();
        assertThat(root.resolve(".campusflow-demo/campusflow-demo.mv.db")).doesNotExist();
    }

    @Test
    void startsTheRealDemoConfigurationAndReportsReadinessWithoutADatabaseFile() throws Exception {
        writeCredentials();
        assertThat(runProbe(WebProbe.class)).isZero();
        assertThat(root.resolve(".campusflow-demo/campusflow-demo.mv.db")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "--spring.datasource.url=jdbc:mysql://invalid.invalid/campusflow",
            "--spring.datasource.hikari.jdbc-url=jdbc:mysql://invalid.invalid/campusflow",
            "--spring.flyway.url=jdbc:mysql://invalid.invalid/campusflow",
            "--spring.config.import=file:./application-local.yml",
            "--spring.profiles.include=mysql-dev",
            "--spring.profiles.active=demo,mysql-dev",
            "--server.address=0.0.0.0",
            "--campusflow.database.jdbc-url=jdbc:mysql://invalid.invalid/campusflow"
    })
    void rejectsForeignConfigurationBeforeCreatingADatabase(String override) throws Exception {
        writeCredentials();
        assertThat(runProbe(override)).isEqualTo(12);
        assertThat(root.resolve(".campusflow-demo/campusflow-demo.mv.db")).doesNotExist();
    }

    @Test
    void rejectsMalformedDemoCredentialsWithoutEchoingFileContents() throws Exception {
        var directory = Files.createDirectory(root.resolve(".campusflow-demo"));
        Files.writeString(directory.resolve("credentials.json"), "{unterminated-test-marker");
        assertThat(runProbe()).isEqualTo(13);
        assertThat(Files.readString(root.resolve("probe.log"))).doesNotContain("unterminated-test-marker");
    }

    private void writeCredentials() throws Exception {
        var random = new SecureRandom();
        var directory = Files.createDirectory(root.resolve(".campusflow-demo"));
        Files.writeString(directory.resolve("credentials.json"), """
                {"accounts":{
                  "admin":{"username":"demo_admin","password":"Fixture-%s"},
                  "student":{"username":"demo_student","password":"Fixture-%s"},
                  "student2":{"username":"demo_student2","password":"Fixture-%s"}
                }}
                """.formatted(Long.toHexString(random.nextLong()),
                Long.toHexString(random.nextLong()), Long.toHexString(random.nextLong())));
    }

    private int runProbe(String... overrides) throws Exception {
        return runProbe(Probe.class, overrides);
    }

    private int runProbe(Class<?> probe, String... overrides) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        var command = new ArrayList<>(List.of(java.toString(), "-cp", System.getProperty("java.class.path"),
                probe.getName(), "--spring.profiles.active=demo"));
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
        public static void main(String[] args) throws Exception {
            try (var context = new SpringApplicationBuilder(Probe.class).web(WebApplicationType.NONE).run(args)) {
                var source = context.getBean(HikariDataSource.class);
                assertThat(source.getDriverClassName()).isEqualTo("org.h2.Driver");
                assertThat(source.getJdbcUrl()).startsWith("jdbc:h2:mem:campusflow_demo;");
                assertThat(context.getEnvironment().getProperty("server.address")).isEqualTo("127.0.0.1");
                assertThat(context.getEnvironment().getProperty("server.port")).isEqualTo("18087");
                try (var connection = source.getConnection()) {
                    assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
                }
            } catch (IllegalStateException failure) {
                if ("demo startup rejected an unsafe configuration override.".equals(failure.getMessage())) {
                    System.exit(12);
                }
                if ("demo requires valid local credentials in .campusflow-demo/credentials.json.".equals(failure.getMessage())) {
                    System.exit(13);
                }
                throw failure;
            }
        }
    }

    public static final class WebProbe {
        public static void main(String[] args) throws Exception {
            var builder = new SpringApplicationBuilder(CampusFlowApplication.class).web(WebApplicationType.SERVLET)
                    .contextFactory(type -> new GenericWebApplicationContext(new MockServletContext()));
            try (var context = (GenericWebApplicationContext) builder.run(args)) {
                MockMvcBuilders.webAppContextSetup(context).build().perform(get("/api/v1/demo/health"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.authority").value("H2_DEMO"));
            }
        }
    }
}
