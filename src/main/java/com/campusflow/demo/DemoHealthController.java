package com.campusflow.demo;

import com.campusflow.common.api.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("demo")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class DemoHealthController {

    private final ApplicationAvailability availability;

    DemoHealthController(ApplicationAvailability availability) {
        this.availability = availability;
    }

    @GetMapping("/api/v1/demo/health")
    ResponseEntity<ApiResponse<DemoHealth>> health() {
        if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(ApiResponse.success(new DemoHealth("UP", "H2_DEMO")));
    }

    record DemoHealth(String status, String authority) {}
}
