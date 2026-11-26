package com.campusflow.common.config;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
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
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Applies the mysql-test boundary before Spring refreshes the application context. */
public final class MysqlTestRuntime implements EnvironmentPostProcessor, Ordered,
        ApplicationContextInitializer<GenericApplicationContext> {

    private static final String PROFILE = "mysql-test";
    private static final String LOCAL_FILE = "application-local.yml";
    private static final String CONFIG_LOCATION = "classpath:/";
    private static final String JDBC_URL = "jdbc:mysql://127.0.0.1:3306/campusflow_test"
            + "?sslMode=REQUIRED&allowPublicKeyRetrieval=false&connectTimeout=3000&socketTimeout=10000"
            + "&connectionTimeZone=%2B00:00&forceConnectionTimeZoneToSession=true&autoReconnect=false";
    private static final Set<String> BUNDLED_CONFIG = Set.of("application.yml", "application-test.yml",
            "application-mysql-test.yml");
    private static final Set<String> RESTRICTED_NAMESPACES = Set.of("spring", "server", "management", "campusflow",
            "logging", "debug", "trace", "hibernate", "jakarta.persistence", "javax.persistence", "hikaricp",
            "jdbc", "com.mysql.cj", "java.net.usesystemproxies", "socksproxyhost", "socksproxyport",
            "socksproxyversion", "socksnonproxyhosts");
    private static final Map<String, String> REQUIRED_SETTINGS = Map.of(
            "server.address", "127.0.0.1",
            "server.port", "18086",
            "spring.flyway.enabled", "false",
            "spring.sql.init.mode", "never",
            "spring.jpa.hibernate.ddl-auto", "validate",
            "spring.jpa.properties.hibernate.jdbc.time_zone", "UTC",
            "spring.jpa.properties.hibernate.default_catalog", "campusflow_test",
            "campusflow.auth.issuer", "campusflow-test",
            "campusflow.auth.audience", "campusflow-test-api");

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER - 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Boot has not populated the active profiles from command-line properties at this point.
        var configuredProfiles = Binder.get(environment).bind("spring.profiles.active", String[].class)
                .orElseGet(() -> new String[0]);
        if (Arrays.asList(configuredProfiles).contains(PROFILE) || application.getAdditionalProfiles().contains(PROFILE)
                || environment.matchesProfiles(PROFILE)) {
            verifySources(environment);
            environment.getPropertySources().addFirst(new MapPropertySource("campusflowMysqlTestConfigLocation",
                    Map.of("spring.config.location", CONFIG_LOCATION)));
        }
    }

    @Override
    public void initialize(GenericApplicationContext context) {
        var environment = context.getEnvironment();
        if (!environment.matchesProfiles(PROFILE)) {
            return;
        }
        verifyConfiguration(environment);
        var secrets = loadLocalSecrets();
        environment.getPropertySources().addFirst(new MapPropertySource("campusflowMysqlTestSigningKey",
                Map.of("campusflow.auth.signing-key-base64", secrets.signingKey())));
        context.registerBean("dataSource", HikariDataSource.class, () -> fixedDataSource(secrets.password()),
                bean -> bean.setDestroyMethodName("close"));
    }

    private static void verifyConfiguration(ConfigurableEnvironment environment) {
        verifySources(environment);
        if (REQUIRED_SETTINGS.entrySet().stream()
                .anyMatch(setting -> !setting.getValue().equals(environment.getProperty(setting.getKey())))) {
            throw unsafeConfiguration();
        }
    }

    private static void verifySources(ConfigurableEnvironment environment) {
        if (Arrays.stream(environment.getActiveProfiles()).anyMatch(profile -> !PROFILE.equals(profile))) {
            throw unsafeConfiguration();
        }
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (ConfigurationPropertySources.isAttachedConfigurationPropertySource(source)
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
        if (OriginLookup.getOrigin(source, rawName) instanceof TextResourceOrigin textOrigin) {
            var resource = textOrigin.getResource();
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
        Object value = source.getProperty(rawName);
        if (("spring.profiles.active".equals(name) && PROFILE.equals(value))
                || ("spring.config.location".equals(name) && CONFIG_LOCATION.equals(value))) {
            return;
        }
        throw unsafeConfiguration();
    }

    private static LocalSecrets loadLocalSecrets() {
        var root = Path.of("").toAbsolutePath();
        var file = root.resolve(LOCAL_FILE);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || !file.toRealPath().equals(root.toRealPath().resolve(LOCAL_FILE))) {
                throw invalidCredentials();
            }
            var options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(0);
            options.setCodePointLimit(16_384);
            try (var reader = new InputStreamReader(Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS),
                    UTF_8.newDecoder())) {
                Object document = new Yaml(new SafeConstructor(options)).load(reader);
                var local = child(child(document, "campusflow"), "local");
                var password = child(child(local, "database"), "test-app-password");
                var signingKey = child(child(local, "jwt"), "test-signing-key-base64");
                if (!(password instanceof String literal) || literal.isBlank()
                        || !(signingKey instanceof String encoded)) {
                    throw invalidCredentials();
                }
                byte[] key = Base64.getDecoder().decode(encoded);
                try {
                    if (key.length < 32) {
                        throw invalidCredentials();
                    }
                } finally {
                    Arrays.fill(key, (byte) 0);
                }
                return new LocalSecrets(literal, encoded);
            }
        } catch (IOException | RuntimeException failure) {
            // A YAML parser error can include the input line. Keep the credential out of the failure chain.
            throw invalidCredentials();
        }
    }

    private static Object child(Object node, String key) {
        return node instanceof Map<?, ?> map ? map.get(key) : null;
    }

    private static HikariDataSource fixedDataSource(String password) {
        var dataSource = new HikariDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setJdbcUrl(JDBC_URL);
        dataSource.setUsername("campusflow_example_app");
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(0);
        dataSource.setConnectionTimeout(4_000);
        return dataSource;
    }

    private static IllegalStateException unsafeConfiguration() {
        return new IllegalStateException("mysql-test startup rejected an unsafe configuration override.");
    }

    private static IllegalStateException invalidCredentials() {
        return new IllegalStateException("mysql-test requires its application password and Base64 signing key in application-local.yml.");
    }

    private record LocalSecrets(String password, String signingKey) {
        @Override
        public String toString() {
            return "LocalSecrets[redacted]";
        }
    }
}
