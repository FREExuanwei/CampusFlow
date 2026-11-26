package com.campusflow.modules.user;

import com.campusflow.modules.user.entity.Role;
import com.campusflow.common.config.H2MigrationTestConfiguration;
import com.campusflow.modules.user.entity.UserAccount;
import com.campusflow.modules.user.enums.RoleCode;
import com.campusflow.modules.user.enums.UserStatus;
import com.campusflow.modules.user.repository.RoleRepository;
import com.campusflow.modules.user.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:campusflow_persistence;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@ActiveProfiles("persistence-test")
@Import(H2MigrationTestConfiguration.class)
@Transactional
class UserPersistenceTests {

    // 合成的格式样本，仅用于验证实体和持久化边界。
    private static final String TEST_HASH = "$2b$12$" + "A".repeat(53);

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void migrationsApplyAndSeedExactlyTheTwoBaseRoles() {
        assertThat(flyway.info().applied())
                .extracting(MigrationInfo::getVersion)
                .extracting(Object::toString)
                .startsWith("1", "2");
        assertThat(jdbc.queryForList("SELECT role_code FROM roles ORDER BY role_code", String.class))
                .containsExactly("ADMIN", "STUDENT");
        assertThat(flyway.getConfiguration().isCleanDisabled()).isTrue();
        assertThat(flyway.getConfiguration().isCreateSchemas()).isFalse();
        assertThat(flyway.getConfiguration().isBaselineOnMigrate()).isFalse();
    }

    @Test
    void storesCanonicalUserWithStudentRoleAndTimestamps() {
        var saved = users.saveAndFlush(UserAccount.registerStudent("student_one", TEST_HASH, "示例学生", student()));
        assertThat(saved.getId()).isPositive();
        entityManager.clear();

        var loaded = users.findByUsername("student_one").orElseThrow();
        assertThat(loaded.getDisplayName()).isEqualTo("示例学生");
        assertThat(loaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(loaded.getRoles()).extracting(Role::getCode).containsExactly(RoleCode.STUDENT);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull().isAfterOrEqualTo(loaded.getCreatedAt());
        String storedHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, saved.getId());
        assertThat(storedHash).isEqualTo(TEST_HASH);
    }

    @Test
    void databaseRejectsDuplicateUsernames() {
        var role = student();
        users.saveAndFlush(UserAccount.registerStudent("duplicate_name", TEST_HASH, "第一条", role));
        assertThatThrownBy(() -> users.saveAndFlush(
                UserAccount.registerStudent("duplicate_name", TEST_HASH, "第二条", role)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownAccountStatus() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO users (username, password_hash, display_name, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "invalid_status", TEST_HASH, "测试数据", "UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_status");
    }

    @Test
    void registrationCannotInsertAdminOrStorePlaintext() {
        var admin = roles.findByCode(RoleCode.ADMIN).orElseThrow();
        var student = student();
        assertThatThrownBy(() -> UserAccount.registerStudent("valid_name", TEST_HASH, "示例", admin))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.registerStudent("valid_name", "not-a-hash", "示例", student))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userChangesPreserveIdentityHashAndRole() {
        var saved = users.saveAndFlush(UserAccount.registerStudent("profile_test", TEST_HASH, "旧显示名", student()));
        saved.rename("新显示名");
        saved.changeStatus(UserStatus.DISABLED);
        users.flush();
        entityManager.clear();

        var loaded = users.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getUsername()).isEqualTo("profile_test");
        assertThat(loaded.getDisplayName()).isEqualTo("新显示名");
        assertThat(loaded.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(loaded.getRoles()).extracting(Role::getCode).containsExactly(RoleCode.STUDENT);
        String storedHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, saved.getId());
        assertThat(storedHash).isEqualTo(TEST_HASH);
    }

    @Test
    void staleProfileEditDoesNotRestoreAnAccountDisabledConcurrently() {
        var independent = new TransactionTemplate(transactions);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Long id = independent.execute(status -> users.saveAndFlush(
                UserAccount.registerStudent("stale_profile_edit", TEST_HASH, "旧显示名", student())).getId());
        var profileEdit = users.findById(id).orElseThrow();
        assertThat(profileEdit.getStatus()).isEqualTo(UserStatus.ACTIVE);

        independent.executeWithoutResult(status -> users.findById(id).orElseThrow().changeStatus(UserStatus.DISABLED));

        profileEdit.rename("新显示名");
        users.flush();
        entityManager.clear();
        var current = users.findById(id).orElseThrow();
        assertThat(current.getDisplayName()).isEqualTo("新显示名");
        assertThat(current.getStatus()).isEqualTo(UserStatus.DISABLED);
    }

    private Role student() {
        return roles.findByCode(RoleCode.STUDENT).orElseThrow();
    }
}
