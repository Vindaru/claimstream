package com.claimstream.intake.claims;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * The fast path in Redis. {@code SET key IN_PROGRESS NX EX 30} ("SETNX") is atomic: of 50 simultaneous callers,
 * exactly one gets {@code true}. The other 49 are turned away in about a millisecond, before touching Postgres.
 *
 * <p>Redis is an optimisation, not the guarantee: keys expire and a Redis failover can lose them. If Redis is down,
 * every caller is let through and Postgres's unique constraints still keep exactly one claim.
 */
@Component
public class IdempotencyGate {

    /** Short, so a server that dies mid-request only blocks retries for 30 seconds. */
    static final Duration IN_PROGRESS_TTL = Duration.ofSeconds(30);
    /** Long, so retries within a day are answered from Redis without touching Postgres. */
    static final Duration ANSWER_TTL = Duration.ofHours(24);
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyGate.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public IdempotencyGate(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    /** What Redis knows about a key someone else holds. */
    public sealed interface Held permits InProgress, Done {
    }

    public record InProgress() implements Held {
    }

    public record Done(StoredResponse response) implements Held {
    }

    /** @return true if this caller now owns the key (or Redis is down and we fall back to Postgres) */
    public boolean tryAcquire(String key) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(redisKey(key), IN_PROGRESS, IN_PROGRESS_TTL));
        } catch (RuntimeException e) {
            LOG.warn("Redis unavailable, falling back to Postgres for idempotency: {}", e.getMessage());
            return true;
        }
    }

    public Optional<Held> held(String key) {
        try {
            String value = redis.opsForValue().get(redisKey(key));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(IN_PROGRESS.equals(value) ? new InProgress()
                    : new Done(json.readValue(value, StoredResponse.class)));
        } catch (RuntimeException | JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public void complete(String key, StoredResponse response) {
        try {
            redis.opsForValue().set(redisKey(key), json.writeValueAsString(response), ANSWER_TTL);
        } catch (RuntimeException | JsonProcessingException e) {
            LOG.warn("Could not cache idempotent response for {}: {}", key, e.getMessage());
        }
    }

    /** Frees the key after a failure, so the client's retry can try again. */
    public void release(String key) {
        try {
            redis.delete(redisKey(key));
        } catch (RuntimeException e) {
            LOG.warn("Could not release idempotency key {}: {}", key, e.getMessage());
        }
    }

    private static String redisKey(String key) {
        return "idem:" + key;
    }
}
