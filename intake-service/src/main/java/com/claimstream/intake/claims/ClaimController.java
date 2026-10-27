package com.claimstream.intake.claims;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/claims} with an {@code Idempotency-Key} header. Send the same request with the same key as
 * many times as you like (timeouts, retries, double clicks): the claim is stored once and every call gets the same
 * answer. Replays carry {@code Idempotent-Replayed: true}.
 */
@RestController
@RequestMapping("/api/v1/claims")
public class ClaimController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";

    private final ClaimIntakeService intake;
    private final ClaimStore store;

    public ClaimController(ClaimIntakeService intake, ClaimStore store) {
        this.intake = intake;
        this.store = store;
    }

    @PostMapping
    public ResponseEntity<ClaimResponse> submit(@RequestHeader(IDEMPOTENCY_KEY) String key,
                                                @RequestBody ClaimRequest request) {
        if (key.isBlank() || key.length() > 128) {
            throw new InvalidClaimException("Idempotency-Key must be 1 to 128 characters");
        }
        ClaimIntakeService.Result result = intake.submit(key, request);
        return ResponseEntity.status(result.response().status())
                .header(REPLAYED, String.valueOf(result.replayed()))
                .body(result.response().body());
    }

    @GetMapping("/{claimId}")
    public ResponseEntity<ClaimResponse> find(@PathVariable String claimId) {
        return ResponseEntity.of(store.findClaim(claimId));
    }
}
