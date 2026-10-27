package com.claimstream.intake.claims;

import com.claimstream.domain.Claim;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Receives a claim exactly once per Idempotency-Key.
 *
 * <ol>
 *   <li>Validate the body by building the domain {@link Claim} (400 if invalid).</li>
 *   <li>Redis SETNX: only one caller per key proceeds; the rest get the stored answer or 409 "in progress".</li>
 *   <li>Postgres: insert key + claim in one transaction; unique constraints are the final guarantee.</li>
 *   <li>Remember the answer in Redis and Postgres, so every retry gets the same response.</li>
 * </ol>
 */
@Service
public class ClaimIntakeService {

    private final IdempotencyGate gate;
    private final ClaimStore store;
    private final ObjectMapper json;

    public ClaimIntakeService(IdempotencyGate gate, ClaimStore store, ObjectMapper json) {
        this.gate = gate;
        this.store = store;
        this.json = json;
    }

    /** @param replayed true when this is a stored answer to an earlier request with the same key */
    public record Result(StoredResponse response, boolean replayed) {
    }

    public Result submit(String key, ClaimRequest request) {
        Claim claim = request.toClaim();
        String hash = sha256(request);

        if (!gate.tryAcquire(key)) {
            return gate.held(key)
                    .map(held -> switch (held) {
                        case IdempotencyGate.InProgress p -> throw new RequestInProgressException(key);
                        case IdempotencyGate.Done d -> replay(key, hash, d.response());
                    })
                    .orElseThrow(() -> new RequestInProgressException(key)); // key expired between calls: retry
        }
        try {
            // Redis may have lost the key (expiry, failover), so Postgres is checked before inserting.
            var earlier = store.findByKey(key);
            if (earlier.isPresent()) {
                gate.complete(key, earlier.get());
                return replay(key, hash, earlier.get());
            }
            StoredResponse response = new StoredResponse(hash, HttpStatus.CREATED.value(),
                    ClaimResponse.received(claim));
            store.save(key, claim, response);
            gate.complete(key, response);
            return new Result(response, false);
        } catch (ClaimStore.KeyAlreadyStoredException e) {
            StoredResponse winner = store.findByKey(key).orElseThrow();
            gate.complete(key, winner);
            return replay(key, hash, winner);
        } catch (ClaimStore.ClaimAlreadyStoredException e) {
            gate.release(key);
            throw new DuplicateClaimException(claim.id().value());
        } catch (RuntimeException e) {
            gate.release(key);
            throw e;
        }
    }

    private static Result replay(String key, String hash, StoredResponse stored) {
        if (!stored.requestHash().equals(hash)) {
            throw new IdempotencyKeyReusedException(key);
        }
        return new Result(stored, true);
    }

    private String sha256(ClaimRequest request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
