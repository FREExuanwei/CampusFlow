package com.campusflow.modules.user.controller;

import com.campusflow.common.api.ApiResponse;
import com.campusflow.common.api.PageResponse;
import com.campusflow.common.config.DatabaseWebModule;
import com.campusflow.common.security.CampusFlowPrincipal;
import com.campusflow.common.security.JwtTokens;
import com.campusflow.modules.user.dto.UserProfile;
import com.campusflow.modules.user.dto.UserRequests;
import com.campusflow.modules.user.enums.UserStatus;
import com.campusflow.modules.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@DatabaseWebModule
@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<UserProfile> register(@Valid @RequestBody UserRequests.Register request) {
        return ApiResponse.success(users.register(request));
    }

    @PostMapping("/auth/login")
    ApiResponse<JwtTokens.AccessToken> login(@Valid @RequestBody UserRequests.Login request) {
        return ApiResponse.success(users.login(request));
    }

    @GetMapping("/users/me")
    ApiResponse<UserProfile> me(@AuthenticationPrincipal CampusFlowPrincipal actor) {
        return ApiResponse.success(users.profile(actor.id()));
    }

    @PatchMapping("/users/me")
    ApiResponse<UserProfile> rename(@AuthenticationPrincipal CampusFlowPrincipal actor,
                                    @Valid @RequestBody UserRequests.Rename request) {
        return ApiResponse.success(users.rename(actor.id(), request.displayName()));
    }

    @GetMapping("/admin/users")
    ApiResponse<PageResponse<UserProfile>> list(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) @Pattern(regexp = "[A-Za-z0-9_]{1,32}") String username,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(users.search(status, username, page, size));
    }

    @GetMapping("/admin/users/{id}")
    ApiResponse<UserProfile> detail(@PathVariable @Positive long id) {
        return ApiResponse.success(users.profile(id));
    }

    @PatchMapping("/admin/users/{id}/status")
    ApiResponse<UserProfile> status(@PathVariable @Positive long id,
                                     @AuthenticationPrincipal CampusFlowPrincipal actor,
                                     @Valid @RequestBody UserRequests.ChangeStatus request) {
        return ApiResponse.success(users.updateStatus(id, actor.id(), request.status()));
    }
}
