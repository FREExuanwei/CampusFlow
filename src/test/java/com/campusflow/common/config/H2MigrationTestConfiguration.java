package com.campusflow.common.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.sql.init.dependency.DatabaseInitializationDependencyConfigurer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@TestConfiguration(proxyBeanMethods = false)
@Import(DatabaseInitializationDependencyConfigurer.class)
public class H2MigrationTestConfiguration {

    @Bean
    Flyway h2Flyway(@Value("${spring.datasource.url}") String url) {
        if (!url.startsWith("jdbc:h2:mem:")) {
            throw new IllegalStateException("Test migrations require an H2 in-memory database.");
        }
        return Flyway.configure().dataSource(url, "sa", "")
                .cleanDisabled(true).createSchemas(false).baselineOnMigrate(false).load();
    }

    @Bean
    FlywayMigrationInitializer h2MigrationInitializer(Flyway h2Flyway) {
        return new FlywayMigrationInitializer(h2Flyway);
    }
}
