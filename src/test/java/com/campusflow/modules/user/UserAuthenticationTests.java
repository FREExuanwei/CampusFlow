package com.campusflow.modules.user;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import com.campusflow.common.config.H2MigrationTestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.location=classpath:/application.yml",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@ActiveProfiles({"test", "persistence-test"})
@Import(H2MigrationTestConfiguration.class)
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class UserAuthenticationTests {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String ME = "/api/v1/users/me";
    private static final String ADMIN_USERS = "/api/v1/admin/users";
    private static final String TEST_PASSWORD = "synthetic-password-for-tests";
    private static final String ISSUER = "campusflow";
    private static final String AUDIENCE = "campusflow-api";
    private static final String FIXTURE_SIGNING_KEY_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static final String DATABASE_URL = "jdbc:h2:mem:campusflow_auth_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    @DynamicPropertySource
    static void configureFixtures(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
        registry.add("campusflow.auth.signing-key-base64", () -> FIXTURE_SIGNING_KEY_BASE64);
        registry.add("campusflow.auth.issuer", () -> ISSUER);
        registry.add("campusflow.auth.audience", () -> AUDIENCE);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registrationReturnsANormalizedStudentProfile() throws Exception {
        mvc.perform(registration("Student_One", TEST_PASSWORD, "Student One"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.username").value("student_one"))
                .andExpect(jsonPath("$.data.displayName").value("Student One"))
                .andExpect(jsonPath("$.data.roles", contains("STUDENT")))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.createdAt").isString())
                .andExpect(jsonPath("$.data.password").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.passwordHash").doesNotHaveJsonPath());
    }

    @Test
    void registrationStoresABcryptHash() throws Exception {
        long id = register("stored_password", "Credential fixture");

        String storedCredential = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, id);
        assertThat(storedCredential != null && storedCredential.matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}"))
                .as("the stored credential is a BCrypt hash")
                .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRegistrations")
    void invalidRegistrationsUseTheErrorEnvelope(String description, String username, String password,
                                                  String displayName) throws Exception {
        mvc.perform(registration(username, password, displayName))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private static Stream<Arguments> invalidRegistrations() {
        return Stream.of(
                Arguments.of("username too short", "ab", TEST_PASSWORD, "Student"),
                Arguments.of("username too long", "u".repeat(33), TEST_PASSWORD, "Student"),
                Arguments.of("username contains punctuation", "invalid-username", TEST_PASSWORD, "Student"),
                Arguments.of("username contains non-ASCII letters", "测试用户", TEST_PASSWORD, "Student"),
                Arguments.of("password too short", "invalid_password", "x".repeat(11), "Student"),
                Arguments.of("password exceeds the character limit", "invalid_password", "x".repeat(65), "Student"),
                Arguments.of("password exceeds the BCrypt byte limit", "invalid_password", "界".repeat(25), "Student"),
                Arguments.of("display name is blank", "invalid_display_name", TEST_PASSWORD, " "),
                Arguments.of("display name too long", "invalid_display_name", TEST_PASSWORD, "d".repeat(81)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"role\": \"ADMIN\"", "\"roles\": [\"ADMIN\"]", "\"status\": \"DISABLED\""})
    void registrationRejectsServerManagedFields(String extraField) throws Exception {
        mvc.perform(json(post(REGISTER), """
                        {"username":"overposted_user", "password":"%s", "displayName":"Student", %s}
                        """.formatted(TEST_PASSWORD, extraField)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, "overposted_user"))
                .isZero();
    }

    @Test
    void concurrentRegistrationsOfTheSameUsernameCreateOneAccount(CapturedOutput output) throws Exception {
        var start = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var upper = executor.submit(() -> registerAfterBarrier(start, "Race_User"));
            var lower = executor.submit(() -> registerAfterBarrier(start, "race_user"));

            assertThat(List.of(upper.get(10, TimeUnit.SECONDS), lower.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(HttpStatus.CREATED.value(), HttpStatus.CONFLICT.value());
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, "race_user"))
                .isEqualTo(1);
        assertThat(output.getAll().contains("Unique index or primary key violation"))
                .as("the database error stays out of the application log")
                .isFalse();
    }

    @Test
    void loginReturnsABearerTokenForTheRegisteredAccount() throws Exception {
        long id = register("login_student", "Login Student");
        String token = login("Login_Student", TEST_PASSWORD);

        mvc.perform(bearer(get(ME), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(is(id), Long.class))
                .andExpect(jsonPath("$.data.username").value("login_student"))
                .andExpect(jsonPath("$.data.roles", contains("STUDENT")))
                .andExpect(jsonPath("$.data.passwordHash").doesNotHaveJsonPath());
    }

    @Test
    void aPasswordAtTheBcryptByteLimitCanBeUsedForLogin() throws Exception {
        String password = "界".repeat(24);
        mvc.perform(registration("bcrypt_boundary", password, "Byte boundary"))
                .andExpect(status().isCreated());

        login("bcrypt_boundary", password);
    }

    @Test
    void loginFailuresUseTheSamePublicResponse() throws Exception {
        long id = register("login_denial", "Account used for login denials");
        var unknown = deniedLogin("no_such_account", TEST_PASSWORD);
        var wrongPassword = deniedLogin("login_denial", "synthetic-incorrect-password");

        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", id);
        var disabled = deniedLogin("login_denial", TEST_PASSWORD);

        assertThat(wrongPassword).isEqualTo(unknown);
        assertThat(disabled).isEqualTo(unknown);
    }

    @Test
    void currentProfileRequiresABearerToken() throws Exception {
        mvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void currentProfileRejectsInvalidJwtClaimsAndSignatures(CapturedOutput output) throws Exception {
        long id = register("jwt_validation", "Token validity fixture");
        byte[] trustedKey = Base64.getDecoder().decode(FIXTURE_SIGNING_KEY_BASE64);
        byte[] wrongKey = trustedKey.clone();
        wrongKey[0] ^= 1;
        Instant now = Instant.now();
        Instant issued = now.minusSeconds(30);
        Instant expiration = now.plusSeconds(300);

        String trusted = signedFixture(id, ISSUER, AUDIENCE, issued, expiration, trustedKey);
        mvc.perform(bearer(get(ME), trusted))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(is(id), Long.class));

        var rejected = Map.of(
                "malformed token", "not-a-jwt",
                "wrong signature", signedFixture(id, ISSUER, AUDIENCE, issued, expiration, wrongKey),
                "wrong issuer", signedFixture(id, "untrusted-issuer", AUDIENCE, issued, expiration, trustedKey),
                "wrong audience", signedFixture(id, ISSUER, "untrusted-audience", issued, expiration, trustedKey),
                "expired token", signedFixture(id, ISSUER, AUDIENCE, now.minusSeconds(600),
                        now.minusSeconds(300), trustedKey));
        for (var fixture : rejected.entrySet()) {
            mvc.perform(bearer(get(ME), fixture.getValue()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .as(fixture.getKey()).isEqualTo(HttpStatus.UNAUTHORIZED.value()))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data").value(nullValue()));
        }

        String applicationLog = output.getAll();
        assertThat(applicationLog.contains(TEST_PASSWORD) || applicationLog.contains(trusted)
                || rejected.values().stream().anyMatch(applicationLog::contains))
                .as("the application log excludes credentials")
                .isFalse();
    }

    @Test
    void currentProfileUpdatePersistsTheDisplayName() throws Exception {
        long id = register("profile_student", "Original name");
        String token = login("profile_student", TEST_PASSWORD);

        mvc.perform(bearer(json(patch(ME), """
                        {"displayName":"Updated name"}
                        """), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(is(id), Long.class))
                .andExpect(jsonPath("$.data.displayName").value("Updated name"));

        mvc.perform(bearer(get(ME), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Updated name"));
    }

    @Test
    void currentProfileUpdateRejectsFieldsOutsideItsAllowlist() throws Exception {
        register("profile_allowlist", "Original name");
        String token = login("profile_allowlist", TEST_PASSWORD);

        for (String extraField : List.of("\"username\": \"replacement_name\"", "\"roles\": [\"ADMIN\"]",
                "\"status\": \"DISABLED\"", "\"password\": \"another-synthetic-password\"")) {
            mvc.perform(bearer(json(patch(ME), """
                            {"displayName":"Changed name", %s}
                            """.formatted(extraField)), token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        mvc.perform(bearer(get(ME), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Original name"));
    }

    @Test
    void studentsCannotUseAnyAdminUserRoute() throws Exception {
        long id = register("admin_denied", "Student without admin role");
        String token = login("admin_denied", TEST_PASSWORD);

        mvc.perform(bearer(get(ADMIN_USERS), token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(bearer(get(ADMIN_USERS + "/{id}", id), token))
                .andExpect(status().isForbidden());
        mvc.perform(bearer(statusChange(id, "DISABLED"), token))
                .andExpect(status().isForbidden());

        mvc.perform(bearer(get(ME), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void adminAuthorizationReadsCurrentRolesForAnExistingToken() throws Exception {
        long id = register("changing_roles", "Admin role fixture");
        String token = login("changing_roles", TEST_PASSWORD);
        mvc.perform(bearer(get(ADMIN_USERS), token)).andExpect(status().isForbidden());

        grantAdmin(id);
        mvc.perform(bearer(get(ADMIN_USERS), token)).andExpect(status().isOk());

        jdbc.update("""
                DELETE FROM user_roles
                WHERE user_id = ? AND role_id = (SELECT id FROM roles WHERE role_code = 'ADMIN')
                """, id);
        mvc.perform(bearer(get(ADMIN_USERS), token)).andExpect(status().isForbidden());
    }

    @Test
    void adminCanPageThroughUsersUsingZeroBasedIndexes() throws Exception {
        String token = registerAdmin("pagination_admin");
        register("pagination_student", "Student for pagination");

        var first = page(token, 0);
        var second = page(token, 1);

        Number firstId = JsonPath.read(body(first), "$.data.items[0].id");
        Number secondId = JsonPath.read(body(second), "$.data.items[0].id");
        assertThat(firstId).isNotEqualTo(secondId);
    }

    @Test
    void adminCanFilterUsersByUsernameAndStatus() throws Exception {
        String token = registerAdmin("directory_admin");
        long id = register("directory_target", "Directory target");
        mvc.perform(bearer(statusChange(id, "DISABLED"), token)).andExpect(status().isOk());

        mvc.perform(bearer(get(ADMIN_USERS).param("username", "directory_target").param("status", "DISABLED"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(is(id), Long.class))
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotHaveJsonPath());

        mvc.perform(bearer(get(ADMIN_USERS).param("username", "directory_target").param("status", "ACTIVE"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void adminUserListingRejectsInvalidPageArguments() throws Exception {
        String token = registerAdmin("invalid_page_admin");
        mvc.perform(bearer(get(ADMIN_USERS).param("page", "-1"), token))
                .andExpect(status().isBadRequest());
        mvc.perform(bearer(get(ADMIN_USERS).param("size", "101"), token))
                .andExpect(status().isBadRequest());
        mvc.perform(bearer(get(ADMIN_USERS).param("size", "0"), token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanReadUserDetailsAndGetsNotFoundForMissingAccounts() throws Exception {
        String token = registerAdmin("detail_admin");
        long id = register("detail_student", "Student details");
        mvc.perform(bearer(get(ADMIN_USERS + "/{id}", id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(is(id), Long.class))
                .andExpect(jsonPath("$.data.username").value("detail_student"))
                .andExpect(jsonPath("$.data.displayName").value("Student details"))
                .andExpect(jsonPath("$.data.roles", contains("STUDENT")))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.createdAt").isString())
                .andExpect(jsonPath("$.data.passwordHash").doesNotHaveJsonPath());
        mvc.perform(bearer(get(ADMIN_USERS + "/{id}", Long.MAX_VALUE), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(bearer(statusChange(Long.MAX_VALUE, "DISABLED"), token))
                .andExpect(status().isNotFound());
    }

    @Test
    void disablingAnAccountRevokesAccessWithAnExistingToken() throws Exception {
        String adminToken = registerAdmin("status_admin");
        long id = register("status_student", "Status fixture");
        String studentToken = login("status_student", TEST_PASSWORD);

        mvc.perform(bearer(get(ME), studentToken)).andExpect(status().isOk());
        mvc.perform(bearer(statusChange(id, "UNKNOWN"), adminToken))
                .andExpect(status().isBadRequest());
        mvc.perform(bearer(json(patch(ADMIN_USERS + "/{id}/status", id),
                        "{\"status\":\"DISABLED\",\"displayName\":\"Overposted name\"}"), adminToken))
                .andExpect(status().isBadRequest());
        mvc.perform(bearer(get(ME), studentToken)).andExpect(status().isOk());

        mvc.perform(bearer(statusChange(id, "DISABLED"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.displayName").value("Status fixture"));
        mvc.perform(bearer(get(ME), studentToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mvc.perform(bearer(statusChange(id, "ACTIVE"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mvc.perform(bearer(get(ME), studentToken)).andExpect(status().isUnauthorized());
        mvc.perform(bearer(get(ME), login("status_student", TEST_PASSWORD))).andExpect(status().isOk());
    }

    private long register(String username, String displayName) throws Exception {
        var result = mvc.perform(registration(username, TEST_PASSWORD, displayName))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(body(result), "$.data.id");
        return id.longValue();
    }

    private int registerAfterBarrier(CyclicBarrier start, String username) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return mvc.perform(registration(username, TEST_PASSWORD, "Race fixture"))
                .andReturn().getResponse().getStatus();
    }

    private String registerAdmin(String username) throws Exception {
        grantAdmin(register(username, "Admin fixture"));
        return login(username, TEST_PASSWORD);
    }

    private void grantAdmin(long id) {
        assertThat(jdbc.update("""
                INSERT INTO user_roles (user_id, role_id)
                SELECT ?, id FROM roles WHERE role_code = 'ADMIN'
                """, id))
                .isEqualTo(1);
    }

    private String login(String username, String password) throws Exception {
        var result = mvc.perform(loginRequest(username, password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(600))
                .andReturn();
        return JsonPath.read(body(result), "$.data.accessToken");
    }

    private Map<String, Object> deniedLogin(String username, String password) throws Exception {
        var result = mvc.perform(loginRequest(username, password))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").isString())
                .andReturn();
        Map<String, Object> envelope = JsonPath.read(body(result), "$");
        var publicFields = new LinkedHashMap<>(envelope);
        publicFields.remove("timestamp");
        return publicFields;
    }

    private MvcResult page(String token, int index) throws Exception {
        return mvc.perform(bearer(get(ADMIN_USERS).param("page", Integer.toString(index)).param("size", "1"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(index))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andReturn();
    }

    private static MockHttpServletRequestBuilder registration(String username, String password, String displayName) {
        return json(post(REGISTER), """
                {"username":"%s","password":"%s","displayName":"%s"}
                """.formatted(username, password, displayName));
    }

    private static MockHttpServletRequestBuilder loginRequest(String username, String password) {
        return json(post(LOGIN), """
                {"username":"%s","password":"%s"}
                """.formatted(username, password));
    }

    private static MockHttpServletRequestBuilder statusChange(long id, String status) {
        return json(patch(ADMIN_USERS + "/{id}/status", id), """
                {"status":"%s"}
                """.formatted(status));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String signedFixture(long id, String issuer, String audience, Instant issuedAt, Instant expiresAt,
                                        byte[] key) throws Exception {
        Base64.Encoder base64Url = Base64.getUrlEncoder().withoutPadding();
        String header = base64Url.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
                .getBytes(StandardCharsets.UTF_8));
        String claims = base64Url.encodeToString("""
                {"sub":"%s","iss":"%s","aud":["%s"],"iat":%d,"exp":%d}
                """.formatted(id, issuer, audience, issuedAt.getEpochSecond(), expiresAt.getEpochSecond())
                .getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        String signature = base64Url.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        return signingInput + "." + signature;
    }
}
