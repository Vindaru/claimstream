package com.claimstream.outbox;

import java.sql.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polls the outbox and publishes unsent rows to Kafka, then marks them sent.
 *
 * <p>Delivery is <b>at least once</b>: if the app dies after Kafka acknowledged but before the UPDATE commits,
 * the same rows are sent again on restart. Consumers must therefore be idempotent (the M4 lesson again).
 *
 * <p>{@code FOR UPDATE SKIP LOCKED} lets two relay instances share the table without sending the same row
 * twice at the same moment: each locks a different batch.
 */
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);

    private record Row(long id, String topic, String key, String payload) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final int batchSize;

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate tx, KafkaTemplate<String, String> kafka,
                       int batchSize) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.kafka = kafka;
        this.batchSize = batchSize;
    }

    /** Runs every few hundred ms. Keeps going while batches come back full, so a backlog drains quickly. */
    @Scheduled(fixedDelayString = "${claimstream.outbox.poll-ms:200}")
    public void publishPending() {
        try {
            int sent;
            do {
                sent = publishBatch();
            } while (sent == batchSize);
        } catch (RuntimeException e) {
            // Kafka down or slow: rows stay unsent and are retried on the next tick. Nothing is lost.
            LOG.warn("Outbox relay will retry: {}", e.getMessage());
        }
    }

    /** @return how many rows were published */
    public int publishBatch() {
        Integer sent = tx.execute(status -> {
            List<Row> rows = jdbc.query(
                    "SELECT id, topic, msg_key, payload::text FROM outbox WHERE published_at IS NULL"
                            + " ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED",
                    (rs, n) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                    batchSize);
            if (rows.isEmpty()) {
                return 0;
            }
            List<CompletableFuture<?>> acks = new ArrayList<>();
            for (Row row : rows) {
                acks.add(kafka.send(row.topic(), row.key(), row.payload()));
            }
            waitForAll(acks); // throws -> transaction rolls back -> rows stay unsent
            Long[] ids = rows.stream().map(Row::id).toArray(Long[]::new);
            jdbc.update(con -> {
                var ps = con.prepareStatement("UPDATE outbox SET published_at = now() WHERE id = ANY (?)");
                Array array = con.createArrayOf("bigint", ids);
                ps.setArray(1, array);
                return ps;
            });
            return rows.size();
        });
        return sent == null ? 0 : sent;
    }

    public long pendingCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE published_at IS NULL", Long.class);
        return count == null ? 0 : count;
    }

    private static void waitForAll(List<CompletableFuture<?>> acks) {
        try {
            CompletableFuture.allOf(acks.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Kafka", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka did not acknowledge the batch", e);
        }
    }
}
