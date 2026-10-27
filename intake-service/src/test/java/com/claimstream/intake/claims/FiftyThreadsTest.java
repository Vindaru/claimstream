package com.claimstream.intake.claims;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The M4 headline: a client times out and its retry library fires CLM-5002 from 50 threads at the same instant,
 * all with the same Idempotency-Key. Exactly one claim may be stored.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FiftyThreadsTest extends PostgresAndRedis {

    private static final int THREADS = 50;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ClaimStore store;

    @Test
    void fiftySimultaneousRetriesOfClm5002StoreExactlyOneClaim() throws Exception {
        HttpEntity<String> request = request("retry-storm-5002", claimJson("CLM-5002", "150.00"));
        CountDownLatch startingGun = new CountDownLatch(1);
        List<Future<ResponseEntity<ClaimResponse>>> calls = new ArrayList<>();

        try (ExecutorService threads = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < THREADS; i++) {
                calls.add(threads.submit(() -> {
                    startingGun.await(); // all 50 wait here, then fire together
                    return http.postForEntity("/api/v1/claims", request, ClaimResponse.class);
                }));
            }
            startingGun.countDown();
        }

        int created = 0;
        int replayed = 0;
        int inProgress = 0;
        for (Future<ResponseEntity<ClaimResponse>> call : calls) {
            ResponseEntity<ClaimResponse> response = call.get();
            boolean isReplay = "true".equals(response.getHeaders().getFirst(ClaimController.REPLAYED));
            if (response.getStatusCode().value() == 201 && !isReplay) {
                created++;
            } else if (response.getStatusCode().value() == 201) {
                replayed++;
            } else if (response.getStatusCode().value() == 409) {
                inProgress++;
            }
        }
        System.out.printf("%n50 x CLM-5002 -> created %d, replayed %d, 409 in progress %d, rows in claims: %d%n%n",
                created, replayed, inProgress, store.countClaims("CLM-5002"));

        assertThat(store.countClaims("CLM-5002")).isEqualTo(1);
        assertThat(created).isEqualTo(1);
        assertThat(created + replayed + inProgress).isEqualTo(THREADS);

        ResponseEntity<ClaimResponse> later = http.postForEntity("/api/v1/claims", request, ClaimResponse.class);
        assertThat(later.getStatusCode().value()).isEqualTo(201);
        assertThat(later.getHeaders().getFirst(ClaimController.REPLAYED)).isEqualTo("true");
        assertThat(later.getBody().claimId()).isEqualTo("CLM-5002");
    }

    static HttpEntity<String> request(String key, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.set(ClaimController.IDEMPOTENCY_KEY, key);
        }
        return new HttpEntity<>(body, headers);
    }
}
