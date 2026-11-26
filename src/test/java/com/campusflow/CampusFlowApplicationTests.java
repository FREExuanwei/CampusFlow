package com.campusflow;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CampusFlowApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private Environment environment;

    @Test
    void testProfileStartsOnLoopback() {
        assertThat(port).isPositive();
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
    }

    @Test
    void healthOnlyProfileDoesNotExposeBusinessRoutes() throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            for (String route : new String[]{"/api/v1/users/me", "/api/v1/activities", "/api/v1/admin/statistics"}) {
                var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + route))
                        .timeout(Duration.ofSeconds(5)).GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(404);
            }
        }
    }

    @Test
    void healthReturnsTheSuccessEnvelopeOverHttp() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type").orElseThrow())
                    .startsWith("application/json");

            var json = JsonPath.parse(response.body());
            assertThat(json.read("$.success", Boolean.class)).isTrue();
            assertThat(json.read("$.code", String.class)).isEqualTo("SUCCESS");
            assertThat(json.read("$.message", String.class)).isEqualTo("CampusFlow 服务运行正常");
            assertThat(json.read("$.data.status", String.class)).isEqualTo("UP");
            assertThat(json.read("$.data.application", String.class)).isEqualTo("CampusFlow");
            assertThat(Instant.parse(json.read("$.timestamp", String.class))).isNotNull();
        }
    }
}
