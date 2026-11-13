package com.claimstream.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes an event into the {@code outbox} table. It must run inside the caller's transaction, next to the
 * business INSERT: both rows commit together or neither does. That is the whole trick of the outbox pattern.
 *
 * <p>Why not send to Kafka right here? Because "write the DB, then send to Kafka" is two systems with no shared
 * transaction (the dual-write problem): a crash between the two steps saves a claim nobody ever hears about,
 * or announces a claim that was rolled back.
 */
public class OutboxWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OutboxWriter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** MANDATORY: fails fast if someone calls it outside a transaction, which would break the guarantee. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, String key, Object event) {
        jdbc.update("INSERT INTO outbox (topic, msg_key, event_type, payload) VALUES (?, ?, ?, ?::jsonb)",
                topic, key, event.getClass().getSimpleName(), write(event));
    }

    private String write(Object event) {
        try {
            return json.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
