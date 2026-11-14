package com.easyeat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ActuatorDependencyPresenceTest {

    @Test
    @DisplayName("Should find Spring Boot Actuator HealthEndpoint class on classpath")
    void shouldFindHealthEndpointOnClasspath() {
        assertDoesNotThrow(() -> {
            Class.forName("org.springframework.boot.actuate.health.HealthEndpoint");
        }, "org.springframework.boot.actuate.health.HealthEndpoint must be present on classpath");
    }
}

