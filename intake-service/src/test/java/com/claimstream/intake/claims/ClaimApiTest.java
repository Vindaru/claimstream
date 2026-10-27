package com.claimstream.intake.claims;

import static com.claimstream.intake.claims.FiftyThreadsTest.request;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

/** Every answer the intake API can give, over real HTTP, Postgres and Redis. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClaimApiTest extends PostgresAndRedis {

    @Autowired
    private TestRestTemplate http;

    @Test
    void firstCallCreatesTheClaimAndARetryReplaysTheSameAnswer() {
        var first = post("key-4001", claimJson("CLM-4001", "150.00"), ClaimResponse.class);
        var retry = post("key-4001", claimJson("CLM-4001", "150.00"), ClaimResponse.class);

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(first.getHeaders().getFirst(ClaimController.REPLAYED)).isEqualTo("false");
        assertThat(first.getBody()).isEqualTo(new ClaimResponse("CLM-4001", "M-1001", "1234567890", "RECEIVED",
                new BigDecimal("150.00")));
        assertThat(retry.getStatusCode().value()).isEqualTo(201);
        assertThat(retry.getHeaders().getFirst(ClaimController.REPLAYED)).isEqualTo("true");
        assertThat(retry.getBody()).isEqualTo(first.getBody());
    }

    @Test
    void reusingAKeyForADifferentBodyIsRejected() {
        post("key-4002", claimJson("CLM-4002", "150.00"), ClaimResponse.class);

        var reused = post("key-4002", claimJson("CLM-4002", "999.00"), Map.class);

        assertThat(reused.getStatusCode().value()).isEqualTo(422);
        assertThat(reused.getBody()).containsEntry("code", "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void theSameClaimUnderANewKeyIsADuplicate() {
        post("key-4003-a", claimJson("CLM-4003", "150.00"), ClaimResponse.class);

        var again = post("key-4003-b", claimJson("CLM-4003", "150.00"), Map.class);

        assertThat(again.getStatusCode().value()).isEqualTo(409);
        assertThat(again.getBody()).containsEntry("code", "DUPLICATE_CLAIM");
    }

    @Test
    void aMissingKeyOrAnInvalidClaimIsABadRequest() {
        var noKey = post(null, claimJson("CLM-4004", "150.00"), Map.class);
        var badNpi = post("key-4005", claimJson("CLM-4005", "150.00").replace("1234567890", "12345"), Map.class);
        var zeroCharge = post("key-4006", claimJson("CLM-4006", "0.00"), Map.class);

        assertThat(noKey.getStatusCode().value()).isEqualTo(400);
        assertThat(badNpi.getStatusCode().value()).isEqualTo(400);
        assertThat(badNpi.getBody()).containsEntry("code", "INVALID_REQUEST");
        assertThat(zeroCharge.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void aStoredClaimCanBeReadBack() {
        post("key-4007", claimJson("CLM-4007", "150.00"), ClaimResponse.class);

        assertThat(http.getForEntity("/api/v1/claims/CLM-4007", ClaimResponse.class).getBody().status())
                .isEqualTo("RECEIVED");
        assertThat(http.getForEntity("/api/v1/claims/CLM-9999", String.class).getStatusCode().value())
                .isEqualTo(404);
    }

    private <T> ResponseEntity<T> post(String key, String body, Class<T> type) {
        return http.postForEntity("/api/v1/claims", request(key, body), type);
    }
}
