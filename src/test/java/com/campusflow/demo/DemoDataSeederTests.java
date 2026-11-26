package com.campusflow.demo;

import com.campusflow.common.config.DemoRuntime.Account;
import com.campusflow.common.config.DemoRuntime.Credentials;
import com.campusflow.common.config.H2MigrationTestConfiguration;
import com.campusflow.modules.activity.enums.ActivityPhase;
import com.campusflow.modules.activity.enums.ActivityStatus;
import com.campusflow.modules.activity.service.ActivityService;
import com.campusflow.modules.enrollment.service.EnrollmentService;
import com.campusflow.modules.operations.service.OperationsService;
import com.campusflow.modules.user.dto.UserRequests.Login;
import com.campusflow.modules.user.enums.RoleCode;
import com.campusflow.modules.user.repository.UserAccountRepository;
import com.campusflow.modules.user.service.UserService;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.config.location=classpath:/application.yml",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@ActiveProfiles({"test", "persistence-test"})
@Import(H2MigrationTestConfiguration.class)
class DemoDataSeederTests {

    private static final String DB_URL = "jdbc:h2:mem:demo_fixture_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String FIXTURE_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("campusflow.auth.signing-key-base64", () -> FIXTURE_KEY);
    }

    @Autowired private PlatformTransactionManager transactions;
    @Autowired private UserAccountRepository accounts;
    @Autowired private UserService users;
    @Autowired private ActivityService activities;
    @Autowired private EnrollmentService enrollments;
    @Autowired private OperationsService operations;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void repeatedInitializationKeepsRolesCredentialsAndVariedBusinessData() {
        var admin = new Account("demo_admin", UUID.randomUUID().toString());
        var student = new Account("demo_student", UUID.randomUUID().toString());
        var student2 = new Account("demo_student2", UUID.randomUUID().toString());
        var credentials = new Credentials(admin, student, student2);
        var seeder = new DemoDataSeeder(credentials, accounts, users, activities, enrollments, jdbc);
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> seeder.run(new DefaultApplicationArguments()));
        var firstSeed = operations.statistics();
        transaction.executeWithoutResult(status -> seeder.run(new DefaultApplicationArguments()));

        assertThat(operations.statistics()).isEqualTo(firstSeed);
        assertThat(firstSeed.users()).isEqualTo(3);
        assertThat(firstSeed.publishedActivities()).isGreaterThan(5);
        for (var state : ActivityStatus.values()) {
            assertThat(firstSeed.activityStatusCounts().get(state)).isPositive();
        }
        assertThat(firstSeed.activeEnrollments()).isPositive();
        assertThat(firstSeed.cancelledEnrollments()).isPositive();
        for (var account : new Account[] {admin, student, student2}) {
            assertThat(users.login(new Login(account.username(), account.password())).tokenType()).isEqualTo("Bearer");
        }
        var adminUser = accounts.findByUsername(admin.username()).orElseThrow();
        var studentUser = accounts.findByUsername(student.username()).orElseThrow();
        assertThat(users.profile(adminUser.getId()).roles()).containsExactly(RoleCode.ADMIN);
        assertThat(users.profile(studentUser.getId()).roles()).containsExactly(RoleCode.STUDENT);
        assertThat(operations.unreadCount(studentUser.getId())).isGreaterThan(1);
        Long archivedId = jdbc.queryForObject("SELECT id FROM activities WHERE title = ?", Long.class, "老照片里的校园");
        assertThat(activities.publishedDetail(archivedId).phase()).isEqualTo(ActivityPhase.ENDED);
    }
}
