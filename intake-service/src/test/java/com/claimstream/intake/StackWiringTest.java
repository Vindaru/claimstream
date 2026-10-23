package com.claimstream.intake;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves all three tiers are wired: an HTTP call (presentation) reaches Spring (service),
 * and the health check reaches a real Postgres and Redis (data), each started in Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StackWiringTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private TestRestTemplate http;

    @Test
    @SuppressWarnings("unchecked")
    void healthIsUpWithDatabaseAndRedis() {
        ResponseEntity<Map> response = http.getForEntity("/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
        Map<String, Map<String, Object>> components =
                (Map<String, Map<String, Object>>) response.getBody().get("components");
        assertThat(components.get("db")).containsEntry("status", "UP");
        assertThat(components.get("redis")).containsEntry("status", "UP");
    }

    @Test
    void platformEndpointListsTheAdjudicationPipeline() {
        ResponseEntity<Map> response = http.getForEntity("/api/v1/platform", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("service", "claimstream-intake");
        assertThat(response.getBody().get("pipeline").toString()).contains("ELIGIBILITY", "ADJUDICATION");
    }
}
