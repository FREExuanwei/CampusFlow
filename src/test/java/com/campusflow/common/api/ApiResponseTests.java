package com.campusflow.common.api;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTests {

    @Test
    void successPreservesThePayloadAndUsesTheDefaultMessage() {
        ApiResponse<List<String>> response = ApiResponse.success(List.of("UP"));

        assertThat(response.success()).isTrue();
        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.message()).isEqualTo("请求成功");
        assertThat(response.data()).containsExactly("UP");
        assertThat(Instant.parse(response.timestamp())).isNotNull();
    }

    @Test
    void failureUsesThePublicCodeAndMessageWithNullData() {
        var response = ApiResponse.failure(ErrorCode.BUSINESS_ERROR);

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("BUSINESS_ERROR");
        assertThat(response.message()).isEqualTo("业务处理失败");
        assertThat(response.data()).isNull();
        assertThat(Instant.parse(response.timestamp())).isNotNull();
    }
}
