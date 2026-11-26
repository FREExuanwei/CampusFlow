package com.campusflow.modules.user;

import com.campusflow.CampusFlowApplication;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationOnlyConfigurationTests {

    @Test
    void applicationCannotMigrateEvenWhenFlywayIsExplicitlyEnabled() {
        try (var context = new SpringApplicationBuilder(CampusFlowApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.profiles.active=mysql-migrate", "--spring.flyway.enabled=true",
                        "--spring.flyway.target=2",
                        "--spring.flyway.url=jdbc:h2:mem:campusflow_migration_only;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                        "--spring.flyway.user=sa", "--spring.flyway.password=")) {
            assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
            assertThat(context.getBeansOfType(Flyway.class)).isEmpty();
        }
    }
}
