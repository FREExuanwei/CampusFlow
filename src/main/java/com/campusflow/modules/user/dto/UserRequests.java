package com.campusflow.modules.user.dto;

import com.campusflow.modules.user.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserRequests {

    public record Register(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,32}") String username,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotNull @BcryptPassword String password,
            @NotBlank @Size(max = 80) String displayName) {
        @Override
        public String toString() {
            return "Register[redacted]";
        }
    }

    public record Login(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,32}") String username,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @NotNull @BcryptPassword(minLength = 1) String password) {
        @Override
        public String toString() {
            return "Login[redacted]";
        }
    }

    public record Rename(@NotBlank @Size(max = 80) String displayName) {}

    public record ChangeStatus(@NotNull UserStatus status) {}

    private UserRequests() {}
}
