package com.claimstream.intake.claims;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/** Real Postgres and Redis in Docker, started once and shared by every test class that extends this. */
abstract class PostgresAndRedis {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void connect(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    /** CLM-5002 as JSON: Maria's office visit, sent again. */
    static String claimJson(String claimId, String billed) {
        return """
                {"claimId": "%s", "memberId": "M-1001", "providerNpi": "1234567890",
                 "dateOfService": "2026-10-20",
                 "lines": [{"lineNumber": 1, "cptCode": "99213", "billed": %s}]}
                """.formatted(claimId, billed);
    }
}
