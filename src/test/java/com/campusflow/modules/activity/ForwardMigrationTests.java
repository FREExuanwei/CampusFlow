package com.campusflow.modules.activity;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForwardMigrationTests {

    @Test
    void upgradesVersionTwoWithoutChangingExistingUserRoleOrMigrationChecksums() {
        String url = "jdbc:h2:mem:upgrade_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var base = Flyway.configure().dataSource(url, "sa", "").target("2").cleanDisabled(true).load();
        assertThat(base.migrate().migrationsExecuted).isEqualTo(2);
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        String syntheticHash = "$2b$12$" + "B".repeat(53);
        jdbc.update("""
                INSERT INTO users (username, password_hash, display_name, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "version_two_fixture", syntheticHash, "旧版本测试用户");
        long id = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, "version_two_fixture");
        jdbc.update("""
                INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE role_code = 'STUDENT'
                """, id);
        assertThat(checksums(base)).containsExactly(750175495, 1487491732);

        var latest = Flyway.configure().dataSource(url, "sa", "").target("6").cleanDisabled(true).load();
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(4);
        latest.validate();
        assertThat(latest.info().applied()).extracting(info -> info.getVersion().toString())
                .containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(checksums(latest)).startsWith(750175495, 1487491732);
        assertThat(jdbc.queryForObject("SELECT display_name FROM users WHERE id = ?", String.class, id))
                .isEqualTo("旧版本测试用户");
        assertThat(jdbc.queryForObject("""
                SELECT r.role_code FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ?
                """, String.class, id)).isEqualTo("STUDENT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_logs", Long.class)).isZero();
    }

    @Test
    void upgradesVersionSixAndBackfillsExistingActivityAndAccount() {
        String url = "jdbc:h2:mem:iteration_upgrade_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var base = Flyway.configure().dataSource(url, "sa", "").target("6").cleanDisabled(true).load();
        assertThat(base.migrate().migrationsExecuted).isEqualTo(6);
        var historicalChecksums = checksums(base);
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        jdbc.update("""
                INSERT INTO users (username, password_hash, display_name, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "iteration_fixture", "$2b$12$" + "B".repeat(53), "Iteration fixture");
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, "iteration_fixture");
        jdbc.update("""
                INSERT INTO activities (title, description, location, starts_at, ends_at, capacity, status,
                                        created_by, created_at, updated_at, version)
                VALUES ('Existing activity', 'Fixture', 'Library', '2035-01-01 00:00:00', '2035-01-01 01:00:00',
                        3, 'DRAFT', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, userId);

        var latest = Flyway.configure().dataSource(url, "sa", "").cleanDisabled(true).load();
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(2);
        latest.validate();
        assertThat(latest.info().applied()).extracting(info -> info.getVersion().toString())
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(checksums(latest).subList(0, 6)).isEqualTo(historicalChecksums);
        assertThat(jdbc.queryForObject("SELECT category FROM activities", String.class)).isEqualTo("GENERAL");
        assertThat(jdbc.queryForObject("SELECT token_version FROM users WHERE id = ?", Long.class, userId)).isZero();
        assertThatThrownBy(() -> jdbc.update("UPDATE activities SET category = 'UNKNOWN'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET token_version = -1 WHERE id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static List<Integer> checksums(Flyway flyway) {
        return List.of(flyway.info().applied()).stream().map(MigrationInfo::getChecksum).toList();
    }
}
