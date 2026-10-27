package com.claimstream.intake.claims;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Redis is only the fast path. With Redis down, all 50 callers reach Postgres and its keys still allow one claim. */
@SpringBootTest
class RedisDownTest extends PostgresAndRedis {

    @MockitoBean
    private StringRedisTemplate redis;

    @Autowired
    private ClaimIntakeService intake;

    @Autowired
    private ClaimStore store;

    @Test
    @SuppressWarnings("unchecked")
    void postgresAloneStillStoresExactlyOneClaim() throws Exception {
        ValueOperations<String, String> ops = org.mockito.Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("Redis is down"));

        ClaimRequest request = new ClaimRequest("CLM-4100", "M-1001", "1234567890",
                java.time.LocalDate.of(2026, 10, 20),
                List.of(new ClaimRequest.Line(1, "99213", new java.math.BigDecimal("150.00"))));
        CountDownLatch startingGun = new CountDownLatch(1);
        List<Future<ClaimIntakeService.Result>> calls = new ArrayList<>();
        try (ExecutorService threads = Executors.newFixedThreadPool(50)) {
            for (int i = 0; i < 50; i++) {
                calls.add(threads.submit(() -> {
                    startingGun.await();
                    return intake.submit("redis-down-4100", request);
                }));
            }
            startingGun.countDown();
        }

        long created = 0;
        for (Future<ClaimIntakeService.Result> call : calls) {
            created += call.get().replayed() ? 0 : 1;
        }
        assertThat(store.countClaims("CLM-4100")).isEqualTo(1);
        assertThat(created).isEqualTo(1);
    }
}
