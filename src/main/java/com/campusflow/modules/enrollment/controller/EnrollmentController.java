package com.campusflow.modules.enrollment.controller;

import com.campusflow.common.api.ApiResponse;
import com.campusflow.common.api.PageResponse;
import com.campusflow.common.config.DatabaseWebModule;
import com.campusflow.common.security.CampusFlowPrincipal;
import com.campusflow.modules.activity.enums.ActivityPhase;
import com.campusflow.modules.enrollment.dto.AdminEnrollment;
import com.campusflow.modules.enrollment.dto.EnrollmentView;
import com.campusflow.modules.enrollment.dto.MyEnrollment;
import com.campusflow.modules.enrollment.entity.Enrollment.Status;
import com.campusflow.modules.enrollment.service.EnrollmentService;
import com.campusflow.modules.enrollment.service.EnrollmentService.RosterSummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@DatabaseWebModule
@RestController
@RequestMapping("/api/v1")
public class EnrollmentController {

    private final EnrollmentService enrollments;

    public EnrollmentController(EnrollmentService enrollments) {
        this.enrollments = enrollments;
    }

    @PostMapping("/activities/{id}/enrollments")
    ResponseEntity<ApiResponse<EnrollmentView>> enroll(@PathVariable @Positive long id,
                                                       @AuthenticationPrincipal CampusFlowPrincipal actor) {
        var result = enrollments.enroll(id, actor.id());
        var status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(result.enrollment()));
    }

    @DeleteMapping("/activities/{id}/enrollments/me")
    ApiResponse<EnrollmentView> cancel(@PathVariable @Positive long id,
                                       @AuthenticationPrincipal CampusFlowPrincipal actor) {
        return ApiResponse.success(enrollments.cancel(id, actor.id()));
    }

    @GetMapping("/enrollments/me")
    ApiResponse<PageResponse<MyEnrollment>> mine(@AuthenticationPrincipal CampusFlowPrincipal actor,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) ActivityPhase phase,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(enrollments.mine(actor.id(), status, phase, page, size));
    }

    @GetMapping("/activities/{id}/enrollments/me")
    ApiResponse<EnrollmentView> mineByActivity(@PathVariable @Positive long id,
                                                @AuthenticationPrincipal CampusFlowPrincipal actor) {
        return ApiResponse.success(enrollments.mineByActivity(id, actor.id()));
    }

    @GetMapping("/enrollments/me/{id}")
    ApiResponse<MyEnrollment> mineById(@AuthenticationPrincipal CampusFlowPrincipal actor,
                                       @PathVariable @Positive long id) {
        return ApiResponse.success(enrollments.mineById(actor.id(), id));
    }

    @GetMapping("/admin/activities/{id}/enrollments")
    ApiResponse<PageResponse<AdminEnrollment>> roster(@PathVariable @Positive long id,
            @RequestParam(required = false) Status status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(enrollments.roster(id, status, page, size));
    }

    @GetMapping("/admin/activities/{id}/enrollments/summary")
    ApiResponse<RosterSummary> rosterSummary(@PathVariable @Positive long id) {
        return ApiResponse.success(enrollments.rosterSummary(id));
    }
}
