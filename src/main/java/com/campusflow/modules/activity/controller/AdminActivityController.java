package com.campusflow.modules.activity.controller;

import com.campusflow.common.api.ApiResponse;
import com.campusflow.common.api.PageResponse;
import com.campusflow.common.config.DatabaseWebModule;
import com.campusflow.common.security.CampusFlowPrincipal;
import com.campusflow.modules.activity.dto.ActivityForm;
import com.campusflow.modules.activity.dto.ActivityTransitions;
import com.campusflow.modules.activity.dto.ActivityView;
import com.campusflow.modules.activity.enums.ActivityStatus;
import com.campusflow.modules.activity.service.ActivityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/admin/activities")
public class AdminActivityController {

    private final ActivityService activities;

    public AdminActivityController(ActivityService activities) {
        this.activities = activities;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ActivityView> create(@AuthenticationPrincipal CampusFlowPrincipal actor,
                                     @Valid @RequestBody ActivityForm request) {
        return ApiResponse.success(activities.create(actor.id(), request));
    }

    @GetMapping
    ApiResponse<PageResponse<ActivityView>> list(
            @RequestParam(required = false) ActivityStatus status,
            @RequestParam(required = false) @Size(max = 120) String title,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(activities.list(status, title, page, size));
    }

    @GetMapping("/{id}")
    ApiResponse<ActivityView> detail(@PathVariable @Positive long id) {
        return ApiResponse.success(activities.detail(id));
    }

    @PatchMapping("/{id}")
    ApiResponse<ActivityView> edit(@PathVariable @Positive long id,
                                   @AuthenticationPrincipal CampusFlowPrincipal actor,
                                   @Valid @RequestBody ActivityForm request) {
        return ApiResponse.success(activities.edit(id, actor.id(), request));
    }

    @PostMapping("/{id}/submit")
    ApiResponse<ActivityView> submit(@PathVariable @Positive long id,
                                    @AuthenticationPrincipal CampusFlowPrincipal actor,
                                    @Valid @RequestBody ActivityTransitions.Version request) {
        return ApiResponse.success(activities.submit(id, actor.id(), request.version()));
    }

    @PostMapping("/{id}/review")
    ApiResponse<ActivityView> review(@PathVariable @Positive long id,
                                    @AuthenticationPrincipal CampusFlowPrincipal actor,
                                    @Valid @RequestBody ActivityTransitions.Review request) {
        return ApiResponse.success(activities.review(id, actor.id(), request));
    }

    @PostMapping("/{id}/publish")
    ApiResponse<ActivityView> publish(@PathVariable @Positive long id,
                                     @AuthenticationPrincipal CampusFlowPrincipal actor,
                                     @Valid @RequestBody ActivityTransitions.Version request) {
        return ApiResponse.success(activities.publish(id, actor.id(), request.version()));
    }
}
