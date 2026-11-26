package com.campusflow.common.exception;

import com.campusflow.common.api.ErrorCode;
import com.jayway.jsonpath.JsonPath;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(controllers = GlobalExceptionHandlerTests.RequestProbeController.class)
@Import(GlobalExceptionHandlerTests.RequestProbeController.class)
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void invalidBodyReturnsOnlyPublicFieldErrors() throws Exception {
        mvc.perform(post("/test-only/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "email": "bad-private-input",
                                  "note": "more than sixteen characters"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data[*].field", containsInAnyOrder("name", "email", "campusId", "note")))
                .andExpect(jsonPath("$.data[?(@.field == 'name')].message", contains("不得为空白")))
                .andExpect(jsonPath("$.data[?(@.field == 'email')].message", contains("邮箱格式不正确")))
                .andExpect(jsonPath("$.data[?(@.field == 'campusId')].message", contains("不能为空")))
                .andExpect(jsonPath("$.data[?(@.field == 'note')].message", contains("长度或数量超出允许范围")))
                .andExpect(content().string(not(containsString("bad-private-input"))));
    }

    @Test
    void malformedBodyIsReportedWithoutParserDetails() throws Exception {
        mvc.perform(post("/test-only/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":bad_private_json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("bad_private_json"))));
    }

    @Test
    void validationDoesNotEchoClientSuppliedMapKeys() throws Exception {
        mvc.perform(post("/test-only/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Student",
                                  "email": "fixture@example.invalid",
                                  "campusId": 1,
                                  "attributes": {"private-map-key": " "}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data[0].field").value("attributes"))
                .andExpect(content().string(not(containsString("private-map-key"))));
    }

    @Test
    void missingParameterReturnsInvalidRequest() throws Exception {
        mvc.perform(get("/test-only/requests/number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void wrongParameterTypeReturnsInvalidRequest() throws Exception {
        mvc.perform(get("/test-only/requests/number").param("count", "bad-private-input"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().string(not(containsString("bad-private-input"))));
    }

    @Test
    void invalidQueryParameterUsesTheValidationEnvelope() throws Exception {
        mvc.perform(get("/test-only/requests/label").param("label", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data[0].field").value("label"))
                .andExpect(jsonPath("$.data[0].message").value("不得为空白"));
    }

    @Test
    void invalidControllerReturnValueIsAServerError() throws Exception {
        mvc.perform(get("/test-only/requests/invalid-result"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void unexpectedExceptionReturnsOnlyPublicErrorAndLogsCauseTypes(CapturedOutput output) throws Exception {
        mvc.perform(get("/test-only/requests/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("系统内部错误"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("private-diagnostic-marker"))))
                .andExpect(content().string(not(containsString("private-cause-marker"))))
                .andExpect(result -> {
                    String timestamp = JsonPath.read(result.getResponse().getContentAsString(), "$.timestamp");
                    assertThat(Instant.parse(timestamp)).isNotNull();
                });

        assertThat(output.getAll())
                .contains(IllegalStateException.class.getName(), IllegalArgumentException.class.getName())
                .doesNotContain("private-diagnostic-marker", "private-cause-marker");
    }

    @Test
    void businessExceptionUsesThePublicBusinessCodeAndStatus() throws Exception {
        mvc.perform(get("/test-only/requests/business-failure"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("业务处理失败"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void missingRouteUsesTheNotFoundEnvelope() throws Exception {
        mvc.perform(get("/api/v1/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void wrongMethodKeepsTheAllowHeader() throws Exception {
        mvc.perform(post("/test-only/requests/number"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedRequestMediaTypeKeepsHttpStatus() throws Exception {
        mvc.perform(post("/test-only/requests")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @TestComponent
    @RestController
    @RequestMapping("/test-only/requests")
    static class RequestProbeController {

        @PostMapping
        void validate(@Valid @RequestBody RequestProbe request) {
        }

        @GetMapping("/number")
        void number(@RequestParam("count") int count) {
        }

        @GetMapping("/label")
        void label(@NotBlank @RequestParam("label") String label) {
        }

        @NotBlank
        @GetMapping("/invalid-result")
        String invalidResult() {
            return "";
        }

        @GetMapping("/failure")
        void fail() {
            throw new IllegalStateException("private-diagnostic-marker",
                    new IllegalArgumentException("private-cause-marker"));
        }

        @GetMapping("/business-failure")
        void businessFailure() {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR);
        }
    }

    record RequestProbe(@NotBlank String name, @Email String email, @NotNull Integer campusId,
                        @Size(max = 16) String note, Map<String, @NotBlank String> attributes) {
    }
}
