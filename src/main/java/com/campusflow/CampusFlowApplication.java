package com.campusflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class, FlywayAutoConfiguration.class})
public class CampusFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusFlowApplication.class, args);
    }
}
