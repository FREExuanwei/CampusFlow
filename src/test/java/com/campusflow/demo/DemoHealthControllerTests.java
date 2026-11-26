package com.campusflow.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailabilityBean;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class DemoHealthControllerTests {
    @Test
    void reportsH2AuthorityOnlyAfterStartupRunnersHaveFinished() {
        var availability = new ApplicationAvailabilityBean();
        var controller = new DemoHealthController(availability);

        assertThat(controller.health().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        availability.onApplicationEvent(new AvailabilityChangeEvent<>(this, ReadinessState.ACCEPTING_TRAFFIC));
        var ready = controller.health();
        assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ready.getBody().data()).isEqualTo(new DemoHealthController.DemoHealth("UP", "H2_DEMO"));
    }
}
