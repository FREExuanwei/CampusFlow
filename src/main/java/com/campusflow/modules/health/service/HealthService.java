package com.campusflow.modules.health.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final String applicationName;

    public HealthService(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    public HealthDetails health() {
        return new HealthDetails("UP", applicationName);
    }

    public record HealthDetails(String status, String application) {
    }
}
