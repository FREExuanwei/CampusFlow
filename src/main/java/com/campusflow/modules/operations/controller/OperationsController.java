package com.campusflow.modules.operations.controller;

import com.campusflow.common.api.ApiResponse;
import com.campusflow.common.api.PageResponse;
import com.campusflow.common.config.DatabaseWebModule;
import com.campusflow.common.security.CampusFlowPrincipal;
import com.campusflow.modules.operations.service.OperationsService;
import com.campusflow.modules.operations.service.OperationsService.Action;
import com.campusflow.modules.operations.service.OperationsService.Notification;
import com.campusflow.modules.operations.service.OperationsService.Operation;
import com.campusflow.modules.operations.service.OperationsService.Statistics;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@DatabaseWebModule
@RestController
@RequestMapping("/api/v1")
public class OperationsController {

    private final OperationsService operations;

    public OperationsController(OperationsService operations) {
        this.operations = operations;
    }

    @GetMapping("/notifications/me")
    ApiResponse<PageResponse<Notification>> inbox(@AuthenticationPrincipal CampusFlowPrincipal actor,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(operations.inbox(actor.id(), unreadOnly, page, size));
    }

    @PatchMapping("/notifications/{id}/read")
    ApiResponse<Notification> markRead(@AuthenticationPrincipal CampusFlowPrincipal actor,
                                       @PathVariable @Positive long id) {
        return ApiResponse.success(operations.markRead(actor.id(), id));
    }

    @GetMapping("/notifications/{id}")
    ApiResponse<Notification> notification(@AuthenticationPrincipal CampusFlowPrincipal actor,
                                           @PathVariable @Positive long id) {
        return ApiResponse.success(operations.notification(actor.id(), id));
    }

    @GetMapping("/notifications/me/unread-count")
    ApiResponse<UnreadCount> unreadCount(@AuthenticationPrincipal CampusFlowPrincipal actor) {
        return ApiResponse.success(new UnreadCount(operations.unreadCount(actor.id())));
    }

    @PatchMapping("/notifications/me/read-all")
    ApiResponse<ReadAll> markAllRead(@AuthenticationPrincipal CampusFlowPrincipal actor) {
        return ApiResponse.success(new ReadAll(operations.markAllRead(actor.id())));
    }

    record UnreadCount(long unreadCount) {}
    record ReadAll(int updatedCount) {}

    @GetMapping("/admin/statistics")
    ApiResponse<Statistics> statistics() {
        return ApiResponse.success(operations.statistics());
    }

    @GetMapping("/admin/operation-logs")
    ApiResponse<PageResponse<Operation>> audit(
            @RequestParam(required = false) Action action,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(operations.audit(action, page, size));
    }
}
