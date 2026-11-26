package com.campusflow.demo;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.sql.init.dependency.DatabaseInitializationDependencyConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("demo")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(DatabaseInitializationDependencyConfigurer.class)
class DemoDataConfiguration {

    @Bean
    Flyway demoFlyway(DataSource dataSource) throws SQLException {
        // The initializer gives us a fixed local H2 source. Verify the actual connection before Flyway runs.
        try (var connection = dataSource.getConnection()) {
            var metadata = connection.getMetaData();
            if (!"H2".equals(metadata.getDatabaseProductName()) || !metadata.getURL().equals("jdbc:h2:mem:campusflow_demo")
                    || connection.isReadOnly()) {
                throw new IllegalStateException("The local demo requires its own writable H2 memory data source.");
            }
        }
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .cleanDisabled(true).createSchemas(false).baselineOnMigrate(false).load();
    }

    @Bean
    FlywayMigrationInitializer demoMigrationInitializer(Flyway demoFlyway) {
        return new FlywayMigrationInitializer(demoFlyway);
    }
}
