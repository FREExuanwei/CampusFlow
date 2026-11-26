package com.campusflow.modules.activity;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.campusflow.common.config.H2MigrationTestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence-test"})
@Import(H2MigrationTestConfiguration.class)
class ActivityWorkflowTests {

    private static final String DB_URL = "jdbc:h2:mem:activity_workflow_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger();
    private static final String TEST_PASSWORD = "isolated-workflow-fixture";
    private static final String ADMIN_ACTIVITIES = "/api/v1/admin/activities";

    @DynamicPropertySource
    static void isolatedProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("campusflow.auth.signing-key-base64",
                () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void authenticateFixtureUsers() throws Exception {
        adminToken = createUser(true);
        studentToken = createUser(false);
    }

    @Test
    void administratorCreatesListsAndEditsDraftWithOptimisticVersion() throws Exception {
        var starts = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        var ends = starts.plus(2, ChronoUnit.HOURS);
        var created = mvc.perform(post(ADMIN_ACTIVITIES).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(activity("校园读书会", starts, ends, 20)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.version").value(0)).andReturn();
        long id = idOf(created);
        mvc.perform(get(ADMIN_ACTIVITIES + "/" + id).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("校园读书会"));
        mvc.perform(get(ADMIN_ACTIVITIES).param("title", "读书").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].status").value("DRAFT"));
        mvc.perform(patch(ADMIN_ACTIVITIES + "/" + id).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withVersion(activity("更新的读书会", starts, ends, 25), 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.capacity").value(25));
        mvc.perform(patch(ADMIN_ACTIVITIES + "/" + id).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withVersion(activity("过期提交", starts, ends, 20), 0)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT capacity FROM activities WHERE id = ?", Integer.class, id))
                .isEqualTo(25);
    }

    @Test
    void rejectsStudentWritesAndInvalidSchedules() throws Exception {
        var starts = Instant.now().plus(2, ChronoUnit.DAYS);
        mvc.perform(post(ADMIN_ACTIVITIES).header(HttpHeaders.AUTHORIZATION, studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activity("研讨会", starts, starts.plusSeconds(60), 10)))
                .andExpect(status().isForbidden());
        mvc.perform(post(ADMIN_ACTIVITIES).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activity("研讨会", starts, starts.minusSeconds(60), 10)))
                .andExpect(status().isBadRequest());
        mvc.perform(post(ADMIN_ACTIVITIES).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activity("研讨会", starts, starts.plusSeconds(60), 0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicationRequiresReviewAndExposesOnlyPublishedActivity() throws Exception {
        long id = createDraft("活动审核与发布演练", 20);
        String resource = ADMIN_ACTIVITIES + "/" + id;
        String published = "/api/v1/activities/" + id;
        mvc.perform(get(published).header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isNotFound());
        mvc.perform(post(resource + "/publish").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isConflict());
        mvc.perform(post(resource + "/submit").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        mvc.perform(post(resource + "/review").header(HttpHeaders.AUTHORIZATION, studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(resource + "/review").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedAt").isString());
        mvc.perform(post(resource + "/publish").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mvc.perform(get(published).header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("活动审核与发布演练"))
                .andExpect(jsonPath("$.data.reviewedBy").doesNotHaveJsonPath());
        mvc.perform(get("/api/v1/activities").param("title", "审核与发布").header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(id));
    }

    @Test
    void rejectedActivityCanBeEditedBeforeResubmission() throws Exception {
        long id = createDraft("活动驳回测试", 5);
        String resource = ADMIN_ACTIVITIES + "/" + id;
        mvc.perform(post(resource + "/submit").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        mvc.perform(post(resource + "/review").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"decision\":\"REJECT\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(resource + "/review").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"decision\":\"REJECT\",\"reason\":\"请更正地点\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.reviewReason").value("请更正地点"));
        var starts = Instant.now().plus(4, ChronoUnit.DAYS);
        mvc.perform(patch(resource).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withVersion(activity("修订活动", starts, starts.plusSeconds(60), 5), 2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void enrollmentIsIdempotentAndCancellationReleasesSeat() throws Exception {
        long id = createPublished("报名与取消", 1);
        String route = "/api/v1/activities/" + id + "/enrollments";
        String otherStudent = createUser(false);
        long registrationId = idOf(mvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("ENROLLED")).andReturn());
        mvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(registrationId));
        mvc.perform(delete(route + "/me").header(HttpHeaders.AUTHORIZATION, otherStudent))
                .andExpect(status().isNotFound());
        mvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, otherStudent))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CAPACITY_FULL"));
        mvc.perform(delete(route + "/me").header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, otherStudent))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/enrollments/me").header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.items[0].id").value(registrationId));
    }

    @Test
    void concurrentStudentsCannotOversellTheLastSeat() throws Exception {
        long id = createPublished("并发最后一个名额", 1);
        String route = "/api/v1/activities/" + id + "/enrollments";
        String secondStudent = createUser(false);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> enrollmentStatus(start, route, studentToken));
            var second = workers.submit(() -> enrollmentStatus(start, route, secondStudent));
            start.countDown();
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM enrollments WHERE activity_id = ? AND status = 'ENROLLED'", Long.class, id))
                .isEqualTo(1L);
    }

    @Test
    void readsOwnEnrollmentByActivityAndFiltersPersonalPagesByPhase() throws Exception {
        long id = createPublished("Current enrollment lookup", 2);
        String route = "/api/v1/activities/" + id + "/enrollments";
        String other = createUser(false);
        mvc.perform(get(route + "/me").header(HttpHeaders.AUTHORIZATION, studentToken)).andExpect(status().isNotFound());
        long enrollmentId = idOf(mvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(get(route + "/me").header(HttpHeaders.AUTHORIZATION, other)).andExpect(status().isNotFound());
        mvc.perform(get(route + "/me").header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(enrollmentId))
                .andExpect(jsonPath("$.data.status").value("ENROLLED"));
        mvc.perform(get("/api/v1/enrollments/me").param("status", "ENROLLED").param("phase", "UPCOMING")
                        .header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(enrollmentId));
        mvc.perform(get("/api/v1/enrollments/me").param("status", "ENROLLED").param("phase", "ENDED")
                        .header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void inboxIsOwnerScopedAndDuplicateEnrollmentDoesNotNotifyAgain() throws Exception {
        long id = createPublished("只读通知验收", 3);
        String enroll = "/api/v1/activities/" + id + "/enrollments";
        mvc.perform(post(enroll).header(HttpHeaders.AUTHORIZATION, studentToken)).andExpect(status().isCreated());
        mvc.perform(post(enroll).header(HttpHeaders.AUTHORIZATION, studentToken)).andExpect(status().isOk());
        var inbox = mvc.perform(get("/api/v1/notifications/me").param("unreadOnly", "true")
                        .header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("ENROLLMENT_CONFIRMED"))
                .andExpect(jsonPath("$.data.items[0].activityId").value(id)).andReturn();
        long notice = JsonPath.parse(inbox.getResponse().getContentAsString())
                .read("$.data.items[0].id", Number.class).longValue();
        String read = "/api/v1/notifications/" + notice + "/read";
        mvc.perform(patch(read).header(HttpHeaders.AUTHORIZATION, createUser(false)))
                .andExpect(status().isNotFound());
        mvc.perform(patch(read).header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.readAt").isString());
        mvc.perform(get("/api/v1/notifications/me").param("unreadOnly", "true")
                        .header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void statisticsAndAuditAreAdministratorOnlyAndTrackPublishedActivity() throws Exception {
        String statistics = "/api/v1/admin/statistics";
        String logs = "/api/v1/admin/operation-logs";
        mvc.perform(get(statistics).header(HttpHeaders.AUTHORIZATION, studentToken)).andExpect(status().isForbidden());
        mvc.perform(get(logs).header(HttpHeaders.AUTHORIZATION, studentToken)).andExpect(status().isForbidden());
        var previous = mvc.perform(get(statistics).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()).andReturn();
        long count = JsonPath.parse(previous.getResponse().getContentAsString())
                .read("$.data.publishedActivities", Number.class).longValue();
        long id = createPublished("用于验证脱敏的活动标题", 2);
        long pending = createDraft("Pending statistics fixture", 2);
        mvc.perform(post(ADMIN_ACTIVITIES + "/{id}/submit", pending).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        long pendingCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE status = 'SUBMITTED'", Long.class);
        long enrollmentCount = jdbc.queryForObject("SELECT COUNT(*) FROM enrollments", Long.class);
        mvc.perform(get(statistics).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.publishedActivities").value(count + 1))
                .andExpect(jsonPath("$.data.pendingActivities").value(pendingCount))
                .andExpect(jsonPath("$.data.activityStatusCounts.SUBMITTED").value(pendingCount))
                .andExpect(jsonPath("$.data.activityStatusCounts.PUBLISHED").value(count + 1))
                .andExpect(jsonPath("$.data.totalEnrollments").value(enrollmentCount));
        var audit = mvc.perform(get(logs).param("action", "ACTIVITY_PUBLISHED")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].targetId").value(id))
                .andExpect(jsonPath("$.data.items[0].action").value("ACTIVITY_PUBLISHED")).andReturn();
        assertThat(audit.getResponse().getContentAsString())
                .doesNotContain("用于验证脱敏的活动标题", "flow_user_", TEST_PASSWORD, adminToken);
    }

    @Test
    void discoveryFiltersPublishedActivitiesPaginatesAndReportsAvailableSeats() throws Exception {
        var starts = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        long first = createDraft("Discovery tournament A", 1, starts, "SPORTS");
        publish(first);
        long second = createDraft("Discovery tournament B", 2, starts, "SPORTS");
        publish(second);
        createDraft("Discovery tournament private draft", 1, starts, "SPORTS");
        publish(createDraft("Discovery tournament other category", 1, starts, "ACADEMIC"));
        mvc.perform(post("/api/v1/activities/{id}/enrollments", first)
                        .header(HttpHeaders.AUTHORIZATION, studentToken)).andExpect(status().isCreated());

        for (int page = 0; page < 2; page++) {
            mvc.perform(get("/api/v1/activities").header(HttpHeaders.AUTHORIZATION, studentToken)
                            .param("keyword", "TOURNAMENT").param("category", "SPORTS").param("phase", "UPCOMING")
                            .param("startsFrom", starts.toString()).param("startsBefore", starts.plusSeconds(1).toString())
                            .param("size", "1").param("page", Integer.toString(page)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                    .andExpect(jsonPath("$.data.items[0].id").value(page == 0 ? first : second))
                    .andExpect(jsonPath("$.data.items[0].category").value("SPORTS"))
                    .andExpect(jsonPath("$.data.items[0].remainingSeats").value(page == 0 ? 0 : 2));
        }
        mvc.perform(get("/api/v1/activities/{id}", first).header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.remainingSeats").value(0))
                .andExpect(jsonPath("$.data.phase").value("UPCOMING"));
        mvc.perform(get("/api/v1/activities").header(HttpHeaders.AUTHORIZATION, studentToken)
                        .param("startsFrom", starts.toString()).param("startsBefore", starts.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enrollmentDetailsAreOwnerScopedAndAdminCanInspectMinimalPaginatedRoster() throws Exception {
        long activityId = createPublished("Roster test", 2);
        String enrollment = "/api/v1/activities/" + activityId + "/enrollments";
        long id = idOf(mvc.perform(post(enrollment).header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isCreated()).andReturn());
        String detail = "/api/v1/enrollments/me/" + id;
        mvc.perform(get(detail).header(HttpHeaders.AUTHORIZATION, createUser(false))).andExpect(status().isNotFound());
        mvc.perform(get(detail).header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.activityTitle").value("Roster test"))
                .andExpect(jsonPath("$.data.activityStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.startsAt").isString());
        mvc.perform(delete(enrollment + "/me").header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/enrollments/me").param("status", "CANCELLED")
                        .header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(id))
                .andExpect(jsonPath("$.data.items[0].cancelledAt").isString());
        String roster = ADMIN_ACTIVITIES + "/" + activityId + "/enrollments";
        mvc.perform(get(roster).header(HttpHeaders.AUTHORIZATION, studentToken)).andExpect(status().isForbidden());
        mvc.perform(get(roster).param("status", "CANCELLED").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].userId").isNumber())
                .andExpect(jsonPath("$.data.items[0].username").isString())
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotHaveJsonPath());
        mvc.perform(get(roster + "/summary").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.active").value(0)).andExpect(jsonPath("$.data.cancelled").value(1));
    }

    @Test
    void notificationDetailUnreadCountAndReadAllRespectOwnershipAndAreIdempotent() throws Exception {
        long activityId = createPublished("Inbox read all", 2);
        String enroll = "/api/v1/activities/" + activityId + "/enrollments";
        String otherStudent = createUser(false);
        for (String student : java.util.List.of(studentToken, otherStudent)) {
            mvc.perform(post(enroll).header(HttpHeaders.AUTHORIZATION, student)).andExpect(status().isCreated());
        }
        var inbox = mvc.perform(get("/api/v1/notifications/me").header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andReturn();
        long noticeId = JsonPath.parse(inbox.getResponse().getContentAsString())
                .read("$.data.items[0].id", Number.class).longValue();
        String detail = "/api/v1/notifications/" + noticeId;
        String unread = "/api/v1/notifications/me/unread-count";
        String all = "/api/v1/notifications/me/read-all";
        mvc.perform(get(detail).header(HttpHeaders.AUTHORIZATION, otherStudent)).andExpect(status().isNotFound());
        mvc.perform(get(detail).header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.readAt").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get(unread).header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(1));
        for (int expected : new int[]{1, 0}) {
            mvc.perform(patch(all).header(HttpHeaders.AUTHORIZATION, studentToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.updatedCount").value(expected));
        }
        mvc.perform(get(detail).header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.readAt").isString());
        mvc.perform(get(unread).header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(0));
        mvc.perform(get(unread).header(HttpHeaders.AUTHORIZATION, otherStudent))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void concurrentDuplicateEnrollAndCancelWriteOneAuditAndNotificationPerTransition() throws Exception {
        long activityId = createPublished("Duplicate concurrency", 1);
        String route = "/api/v1/activities/" + activityId + "/enrollments";
        assertThat(parallelRequests(() -> post(route))).containsExactlyInAnyOrder(201, 200);
        assertThat(parallelRequests(() -> delete(route + "/me"))).containsExactly(200, 200);
        assertThat(jdbc.queryForList("SELECT status FROM enrollments WHERE activity_id = ?", String.class, activityId))
                .containsExactly("CANCELLED");
        assertThat(jdbc.queryForList("SELECT type FROM notifications WHERE activity_id = ?", String.class, activityId))
                .containsExactlyInAnyOrder("ACTIVITY_APPROVED", "ACTIVITY_PUBLISHED",
                        "ENROLLMENT_CONFIRMED", "ENROLLMENT_CANCELLED");
        assertThat(jdbc.queryForList("""
                SELECT action FROM operation_logs WHERE target_type = 'ENROLLMENT'
                AND target_id IN (SELECT id FROM enrollments WHERE activity_id = ?)
                """, String.class, activityId)).containsExactlyInAnyOrder("ENROLLMENT_CREATED", "ENROLLMENT_CANCELLED");
    }

    @Test
    void failureToWriteNotificationRollsBackEnrollmentCancellationAndAudit() throws Exception {
        long activityId = createPublished("Transactional rollback", 1);
        String route = "/api/v1/activities/" + activityId + "/enrollments";
        jdbc.execute("""
                ALTER TABLE notifications ADD CONSTRAINT ck_fixture_notice_failure
                  CHECK (activity_id <> %d OR type <> 'ENROLLMENT_CONFIRMED')
                """.formatted(activityId));
        try {
            mvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, studentToken))
                    .andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM enrollments WHERE activity_id = ?", Long.class, activityId))
                    .isZero();
        } finally {
            jdbc.execute("ALTER TABLE notifications DROP CONSTRAINT ck_fixture_notice_failure");
        }
        long enrollmentId = idOf(mvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isCreated()).andReturn());
        jdbc.execute("""
                ALTER TABLE notifications ADD CONSTRAINT ck_fixture_cancel_failure
                  CHECK (activity_id <> %d OR type <> 'ENROLLMENT_CANCELLED')
                """.formatted(activityId));
        try {
            mvc.perform(delete(route + "/me").header(HttpHeaders.AUTHORIZATION, studentToken))
                    .andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForObject("SELECT status FROM enrollments WHERE id = ?", String.class, enrollmentId))
                    .isEqualTo("ENROLLED");
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM operation_logs WHERE target_id = ? AND action = 'ENROLLMENT_CANCELLED'
                    """, Long.class, enrollmentId)).isZero();
            mvc.perform(get("/api/v1/activities/{id}", activityId).header(HttpHeaders.AUTHORIZATION, studentToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.remainingSeats").value(0));
        } finally {
            jdbc.execute("ALTER TABLE notifications DROP CONSTRAINT ck_fixture_cancel_failure");
        }
    }

    @Test
    void reviewAuditFailureRollsBackReviewAndRetriesDoNotDuplicateSideEffects() throws Exception {
        long activityId = createDraft("Review rollback fixture", 2);
        String route = ADMIN_ACTIVITIES + "/" + activityId;
        mvc.perform(post(route + "/submit").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        String approval = "{\"version\":1,\"decision\":\"APPROVE\"}";
        jdbc.execute("""
                ALTER TABLE operation_logs ADD CONSTRAINT ck_fixture_review_failure
                  CHECK (target_id <> %d OR target_type <> 'ACTIVITY' OR action <> 'ACTIVITY_APPROVED')
                """.formatted(activityId));
        try {
            mvc.perform(post(route + "/review").header(HttpHeaders.AUTHORIZATION, adminToken)
                            .contentType(MediaType.APPLICATION_JSON).content(approval))
                    .andExpect(status().isInternalServerError());
            mvc.perform(get(route).header(HttpHeaders.AUTHORIZATION, adminToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.data.version").value(1));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE activity_id = ?",
                    Long.class, activityId)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE operation_logs DROP CONSTRAINT ck_fixture_review_failure");
        }
        for (int expected : new int[]{200, 409}) {
            mvc.perform(post(route + "/review").header(HttpHeaders.AUTHORIZATION, adminToken)
                            .contentType(MediaType.APPLICATION_JSON).content(approval))
                    .andExpect(status().is(expected));
        }
        for (int expected : new int[]{200, 409}) {
            mvc.perform(post(route + "/publish").header(HttpHeaders.AUTHORIZATION, adminToken)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
                    .andExpect(status().is(expected));
        }
        assertThat(jdbc.queryForList("SELECT type FROM notifications WHERE activity_id = ?",
                String.class, activityId)).containsExactlyInAnyOrder("ACTIVITY_APPROVED", "ACTIVITY_PUBLISHED");
        assertThat(jdbc.queryForList("""
                SELECT action FROM operation_logs WHERE target_type = 'ACTIVITY' AND target_id = ?
                """, String.class, activityId)).containsExactlyInAnyOrder(
                        "ACTIVITY_CREATED", "ACTIVITY_SUBMITTED", "ACTIVITY_APPROVED", "ACTIVITY_PUBLISHED");
    }

    private java.util.List<Integer> parallelRequests(java.util.function.Supplier<MockHttpServletRequestBuilder> request)
            throws Exception {
        var start = new CountDownLatch(1);
        java.util.concurrent.Callable<Integer> task = () -> {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent fixture did not start");
            }
            return mvc.perform(request.get().header(HttpHeaders.AUTHORIZATION, studentToken)).andReturn()
                    .getResponse().getStatus();
        };
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(task);
            var second = workers.submit(task);
            start.countDown();
            return java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }

    private int enrollmentStatus(CountDownLatch start, String route, String token) throws Exception {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent enrollment fixture did not start");
        }
        return mvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, token)).andReturn()
                .getResponse().getStatus();
    }

    private long createPublished(String title, int capacity) throws Exception {
        long id = createDraft(title, capacity);
        publish(id);
        return id;
    }

    private void publish(long id) throws Exception {
        String route = ADMIN_ACTIVITIES + "/" + id;
        mvc.perform(post(route + "/submit").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        mvc.perform(post(route + "/review").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(route + "/publish").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
                .andExpect(status().isOk());
    }

    private long createDraft(String title, int capacity) throws Exception {
        var starts = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        return createDraft(title, capacity, starts, null);
    }

    private long createDraft(String title, int capacity, Instant starts, String category) throws Exception {
        String body = activity(title, starts, starts.plus(2, ChronoUnit.HOURS), capacity);
        if (category != null) {
            body = body.stripTrailing().replaceFirst("}$", ",\"category\":\"" + category + "\"}");
        }
        var result = mvc.perform(post(ADMIN_ACTIVITIES).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated()).andReturn();
        return idOf(result);
    }

    private String createUser(boolean admin) throws Exception {
        String username = "flow_user_" + USER_SEQUENCE.incrementAndGet();
        var registered = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","displayName":"流程测试"}
                                """.formatted(username, TEST_PASSWORD)))
                .andExpect(status().isCreated()).andReturn();
        if (admin) {
            jdbc.update("""
                    INSERT INTO user_roles (user_id, role_id)
                    SELECT ?, id FROM roles WHERE role_code = 'ADMIN'
                    """, idOf(registered));
        }
        var login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, TEST_PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + JsonPath.parse(login.getResponse().getContentAsString())
                .read("$.data.accessToken", String.class);
    }

    private static long idOf(MvcResult result) throws Exception {
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.data.id", Number.class).longValue();
    }

    private static String activity(String title, Instant starts, Instant ends, int capacity) {
        return """
                {"title":"%s","description":"校园社团活动","location":"图书馆",
                 "startsAt":"%s","endsAt":"%s","capacity":%d}
                """.formatted(title, starts, ends, capacity);
    }

    private static String withVersion(String body, long version) {
        return body.stripTrailing().replaceFirst("}$", ",\"version\":" + version + "}");
    }
}
