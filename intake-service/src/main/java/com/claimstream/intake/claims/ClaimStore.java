package com.claimstream.intake.claims;

import com.claimstream.domain.Claim;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Postgres is the source of truth. Both inserts run in one transaction, and the primary keys on
 * {@code idempotency_keys} and {@code claims} make a second copy impossible, even if 50 threads race past
 * every check in Java at the same instant: the database lets exactly one INSERT win.
 */
@Repository
public class ClaimStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public ClaimStore(JdbcTemplate jdbc, TransactionTemplate tx, ObjectMapper json) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.json = json;
    }

    /** Thrown when the idempotency key row already exists: someone else already handled this exact request. */
    public static class KeyAlreadyStoredException extends RuntimeException {
    }

    /** Thrown when the claim id exists under a different key. */
    public static class ClaimAlreadyStoredException extends RuntimeException {
    }

    public void save(String key, Claim claim, StoredResponse response) {
        try {
            tx.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO claims (claim_id, member_id, provider_npi, date_of_service, total_billed,"
                                + " status, lines) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)",
                        claim.id().value(), claim.memberId().value(), claim.providerNpi().value(),
                        Date.valueOf(claim.dateOfService()), claim.totalBilled().amount(), response.body().status(),
                        write(claim.lines()));
                jdbc.update("INSERT INTO idempotency_keys (idempotency_key, request_hash, claim_id, response_status,"
                                + " response_body) VALUES (?, ?, ?, ?, ?::jsonb)",
                        key, response.requestHash(), claim.id().value(), response.status(), write(response.body()));
            });
        } catch (DuplicateKeyException e) {
            // The transaction has rolled back. A failed INSERT waits for the winner to commit, so the winner's
            // key row is visible now: if it's there, this was the same request; if not, the same claim under a new key.
            throw findByKey(key).isPresent() ? new KeyAlreadyStoredException() : new ClaimAlreadyStoredException();
        }
    }

    public Optional<StoredResponse> findByKey(String key) {
        List<StoredResponse> rows = jdbc.query(
                "SELECT request_hash, response_status, response_body FROM idempotency_keys WHERE idempotency_key = ?",
                (rs, n) -> new StoredResponse(rs.getString(1), rs.getInt(2),
                        read(rs.getString(3))), key);
        return rows.stream().findFirst();
    }

    public long countClaims(String claimId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM claims WHERE claim_id = ?", Long.class, claimId);
        return count == null ? 0 : count;
    }

    public Optional<ClaimResponse> findClaim(String claimId) {
        return jdbc.query(
                "SELECT claim_id, member_id, provider_npi, status, total_billed FROM claims WHERE claim_id = ?",
                (rs, n) -> new ClaimResponse(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getBigDecimal(5)), claimId).stream().findFirst();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ClaimResponse read(String body) {
        try {
            return json.readValue(body, ClaimResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
