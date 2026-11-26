package com.campusflow.modules.activity.controller;

import com.campusflow.common.api.ApiResponse;
import com.campusflow.common.api.PageResponse;
import com.campusflow.common.config.DatabaseWebModule;
import com.campusflow.modules.activity.dto.PublishedActivity;
import com.campusflow.modules.activity.dto.PublishedActivityFilter;
import com.campusflow.modules.activity.service.ActivityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@DatabaseWebModule
@RestController
@RequestMapping("/api/v1/activities")
public class PublishedActivityController {

    private final ActivityService activities;

    public PublishedActivityController(ActivityService activities) {
        this.activities = activities;
    }

    @GetMapping
    ApiResponse<PageResponse<PublishedActivity>> list(
            @Valid @ModelAttribute PublishedActivityFilter filter,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(activities.publishedList(filter, page, size));
    }

    @GetMapping("/{id}")
    ApiResponse<PublishedActivity> detail(@PathVariable @Positive long id) {
        return ApiResponse.success(activities.publishedDetail(id));
    }
}
