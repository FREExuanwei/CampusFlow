package com.campusflow.modules.health.controller;

import com.campusflow.common.api.ApiResponse;
import com.campusflow.modules.health.service.HealthService;
import com.campusflow.modules.health.service.HealthService.HealthDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public ApiResponse<HealthDetails> health() {
        return ApiResponse.success("CampusFlow 服务运行正常", healthService.health());
    }
}
