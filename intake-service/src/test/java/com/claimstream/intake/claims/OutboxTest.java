package com.claimstream.intake.claims;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimstream.domain.events.Topics;
import com.claimstream.outbox.OutboxRelay;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * M5: a claim accepted by the API is announced on Kafka exactly once per claim, through the outbox.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OutboxTest extends PostgresAndRedis {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OutboxRelay relay;

    @Test
    void acceptedClaimIsSavedAndAnnouncedTogether() {
        post("key-5101", claimJson("CLM-5101", "150.00"));

        assertThat(outboxRows("CLM-5101")).as("outbox row written in the claim's transaction").isEqualTo(1);
        relay.publishBatch(); // the relay also runs on its own timer; calling it here just saves waiting

        ConsumerRecord<String, String> event = readUntilFound("CLM-5101");
        assertThat(event.key()).as("keyed by member, so Maria's claims stay in order").isEqualTo("M-1001");
        assertThat(event.value()).contains("99213"); // jsonb re-formats the JSON, so match the value only
    }

    @Test
    void retriedRequestDoesNotAnnounceTheClaimTwice() {
        post("key-5102", claimJson("CLM-5102", "150.00"));
        post("key-5102", claimJson("CLM-5102", "150.00")); // network retry, same key

        assertThat(outboxRows("CLM-5102")).isEqualTo(1);
    }

    @Test
    void rejectedDuplicateClaimWritesNoEvent() {
        post("key-5103-a", claimJson("CLM-5103", "150.00"));
        post("key-5103-b", claimJson("CLM-5103", "150.00")); // same claim, new key: 409, rolled back

        assertThat(outboxRows("CLM-5103")).as("the rolled-back transaction took its outbox row with it")
                .isEqualTo(1);
    }

    private void post(String key, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        http.postForEntity("/api/v1/claims", new HttpEntity<>(body, headers), String.class);
    }

    private long outboxRows(String claimId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE payload->>'claimId' = ?",
                Long.class, claimId);
        return count == null ? 0 : count;
    }

    private static ConsumerRecord<String, String> readUntilFound(String claimId) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(Topics.CLAIMS_RECEIVED));
            List<ConsumerRecord<String, String>> seen = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(seen::add);
                var match = seen.stream().filter(r -> r.value().contains(claimId)).findFirst();
                if (match.isPresent()) {
                    return match.get();
                }
            }
            throw new AssertionError("No event for " + claimId + " on " + Topics.CLAIMS_RECEIVED);
        }
    }
}
