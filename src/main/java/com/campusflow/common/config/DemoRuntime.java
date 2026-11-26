package com.campusflow.common.config;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.RandomValuePropertySource;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.boot.origin.OriginTrackedResource;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Rejects outside configuration before ConfigData can load any filesystem Spring configuration. */
public final class DemoRuntime implements EnvironmentPostProcessor, Ordered,
        ApplicationContextInitializer<GenericApplicationContext> {

    private static final String PROFILE = "demo";
    private static final String CONFIG_SOURCE = "campusflowDemoConfigLocation";
    private static final String DIRECTORY = ".campusflow-demo";
    private static final String CREDENTIALS = "credentials.json";
    private static final String JDBC_URL = "jdbc:h2:mem:campusflow_demo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final Set<String> BUNDLED_CONFIG = Set.of("application.yml", "application-demo.yml");
    private static final Set<String> RESTRICTED_NAMESPACES = Set.of("spring", "server", "management", "campusflow",
            "logging", "debug", "trace", "hibernate", "jakarta.persistence", "javax.persistence", "hikaricp",
            "jdbc", "com.mysql.cj", "h2");
    private static final Map<String, String> REQUIRED_SETTINGS = Map.of(
            "server.address", "127.0.0.1",
            "server.port", "18087",
            "spring.sql.init.mode", "never",
            "spring.jpa.hibernate.ddl-auto", "validate",
            "spring.flyway.enabled", "false",
            "campusflow.auth.issuer", "campusflow-demo",
            "campusflow.auth.audience", "campusflow-demo-api");

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER - 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String[] configuredProfiles = Binder.get(environment).bind("spring.profiles.active", String[].class)
                .orElseGet(() -> new String[0]);
        if (Arrays.asList(configuredProfiles).contains(PROFILE) || environment.matchesProfiles(PROFILE)
                || application.getAdditionalProfiles().contains(PROFILE)) {
            verifySources(environment);
            environment.getPropertySources().addFirst(new MapPropertySource(CONFIG_SOURCE,
                    Map.of("spring.config.location", "classpath:/")));
        }
    }

    @Override
    public void initialize(GenericApplicationContext context) {
        var environment = context.getEnvironment();
        if (!environment.matchesProfiles(PROFILE)) {
            return;
        }
        verifySources(environment);
        if (!environment.getPropertySources().contains(CONFIG_SOURCE) || REQUIRED_SETTINGS.entrySet().stream()
                .anyMatch(setting -> !setting.getValue().equals(environment.getProperty(setting.getKey())))) {
            throw unsafeConfiguration();
        }
        Path directory = demoDirectory();
        Credentials credentials = loadCredentials(directory);
        environment.getPropertySources().addFirst(new MapPropertySource("campusflowDemoSigningKey",
                Map.of("campusflow.auth.signing-key-base64", newSigningKey())));
        context.registerBean(Credentials.class, () -> credentials);
        context.registerBean("dataSource", HikariDataSource.class, DemoRuntime::fixedDataSource,
                bean -> bean.setDestroyMethodName("close"));
    }

    private static String newSigningKey() {
        var key = new byte[32];
        new SecureRandom().nextBytes(key);
        try {
            return Base64.getEncoder().encodeToString(key);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static void verifySources(ConfigurableEnvironment environment) {
        if (Arrays.stream(environment.getActiveProfiles()).anyMatch(profile -> !PROFILE.equals(profile))) {
            throw unsafeConfiguration();
        }
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (CONFIG_SOURCE.equals(source.getName())
                    || ConfigurationPropertySources.isAttachedConfigurationPropertySource(source)
                    || source instanceof PropertySource.StubPropertySource || source instanceof RandomValuePropertySource) {
                continue;
            }
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                throw unsafeConfiguration();
            }
            for (String name : enumerable.getPropertyNames()) {
                verifyProperty(enumerable, name);
            }
        }
    }

    private static void verifyProperty(PropertySource<?> source, String rawName) {
        if (OriginLookup.getOrigin(source, rawName) instanceof TextResourceOrigin origin) {
            var resource = origin.getResource();
            if (resource instanceof OriginTrackedResource tracked) {
                resource = tracked.getResource();
            }
            if (resource instanceof ClassPathResource classpath && BUNDLED_CONFIG.contains(classpath.getPath())) {
                return;
            }
            throw unsafeConfiguration();
        }
        String name = rawName.toLowerCase(Locale.ROOT).replace('_', '.');
        if ("spring.application.pid".equals(name) || "spring.application.version".equals(name)
                || RESTRICTED_NAMESPACES.stream().noneMatch(namespace -> name.equals(namespace)
                || name.startsWith(namespace + ".") || name.startsWith(namespace + "["))) {
            return;
        }
        if ("spring.profiles.active".equals(name) && PROFILE.equals(source.getProperty(rawName))) {
            return;
        }
        throw unsafeConfiguration();
    }

    private static Path demoDirectory() {
        try {
            var root = Path.of("").toRealPath();
            var directory = root.resolve(DIRECTORY);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || !directory.equals(directory.toRealPath())) {
                throw invalidCredentials();
            }
            return directory;
        } catch (IOException failure) {
            throw invalidCredentials();
        }
    }

    private static Credentials loadCredentials(Path directory) {
        var file = directory.resolve(CREDENTIALS);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.equals(file.toRealPath())
                    || Files.size(file) > 8_192) {
                throw invalidCredentials();
            }
            JsonNode root;
            try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                root = new JsonMapper().readTree(input);
            }
            var accounts = root.path("accounts");
            return new Credentials(readAccount(accounts.path("admin"), "demo_admin"),
                    readAccount(accounts.path("student"), "demo_student"),
                    readAccount(accounts.path("student2"), "demo_student2"));
        } catch (IOException | RuntimeException failure) {
            // Parser errors may contain the input. Report only the dedicated local path.
            throw invalidCredentials();
        }
    }

    private static Account readAccount(JsonNode node, String expectedUsername) {
        String username = node.path("username").asString();
        String password = node.path("password").asString();
        if (!expectedUsername.equals(username) || !password.matches("[A-Za-z0-9_+\\-=/.]{12,72}")) {
            throw invalidCredentials();
        }
        return new Account(username, password);
    }

    private static HikariDataSource fixedDataSource() {
        var dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl(JDBC_URL);
        dataSource.setUsername("campusflow_demo");
        dataSource.setPassword("");
        dataSource.setMaximumPoolSize(4);
        dataSource.setMinimumIdle(0);
        dataSource.setConnectionTimeout(4_000);
        return dataSource;
    }

    private static IllegalStateException unsafeConfiguration() {
        return new IllegalStateException("demo startup rejected an unsafe configuration override.");
    }

    private static IllegalStateException invalidCredentials() {
        return new IllegalStateException("demo requires valid local credentials in .campusflow-demo/credentials.json.");
    }

    public record Account(String username, String password) {
        @Override
        public String toString() {
            return "DemoAccount[redacted]";
        }
    }

    public record Credentials(Account admin, Account student, Account student2) {
        @Override
        public String toString() {
            return "DemoCredentials[redacted]";
        }
    }
}
