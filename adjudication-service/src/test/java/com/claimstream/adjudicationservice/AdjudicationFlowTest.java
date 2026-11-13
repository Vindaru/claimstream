package com.claimstream.adjudicationservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.claimstream.domain.events.ClaimReceived;
import com.claimstream.domain.events.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * The whole M5 consumer path against real Kafka and Postgres: event in, rules run, verdict saved,
 * verdict event out. Then the three things that go wrong in real life: the same event twice, an event that
 * breaks a rule, and a message that isn't even JSON.
 */
@Testcontainers
@SpringBootTest
class AdjudicationFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> producer;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Test
    void mariasOfficeVisitIsPaidAndAnnounced() throws Exception {
        send(officeVisit("CLM-5001", "M-1001"));

        await().atMost(WAIT).until(() -> status("CLM-5001") != null);
        assertThat(status("CLM-5001")).isEqualTo("PAID");
        // Allowed $110, all of it toward Maria's deductible ($350 of $500 met): the plan pays $0.
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT allowed, plan_pays, member_pays FROM adjudications WHERE claim_id = 'CLM-5001'");
        assertThat((BigDecimal) row.get("allowed")).isEqualByComparingTo("110");
        assertThat((BigDecimal) row.get("plan_pays")).isEqualByComparingTo("0");
        assertThat((BigDecimal) row.get("member_pays")).isEqualByComparingTo("110");

        assertThat(readUntilFound(Topics.CLAIMS_ADJUDICATED, "CLM-5001").value()).contains("PAID");
    }

    @Test
    void lapsedMemberIsDenied() throws Exception {
        send(officeVisit("CLM-5003", "M-2002"));

        await().atMost(WAIT).until(() -> status("CLM-5003") != null);
        assertThat(status("CLM-5003")).isEqualTo("DENIED");
        assertThat(jdbc.queryForObject("SELECT reason_code FROM adjudications WHERE claim_id = 'CLM-5003'",
                String.class)).isEqualTo("COVERAGE_TERMINATED");
    }

    @Test
    void sameEventDeliveredTwiceIsDecidedOnce() throws Exception {
        ClaimReceived event = officeVisit("CLM-5201", "M-9001");
        send(event);
        send(event); // what at-least-once delivery looks like

        await().atMost(WAIT).until(() -> status("CLM-5201") != null);
        // Give the second copy time to arrive and be skipped.
        await().pollDelay(Duration.ofSeconds(2)).atMost(WAIT).until(() -> true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM adjudications WHERE claim_id = 'CLM-5201'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE payload->>'claimId' = 'CLM-5201'",
                Integer.class)).as("one verdict event, not two").isEqualTo(1);
    }

    @Test
    void poisonMessageGoesToTheDeadLetterTopicAndTheNextClaimStillFlows() throws Exception {
        producer.send(Topics.CLAIMS_RECEIVED, "M-9002", "this is not json").get();
        send(officeVisit("CLM-5202", "M-9002")); // same key = same partition, right behind the poison pill

        assertThat(readUntilFound(Topics.CLAIMS_RECEIVED_DLT, "this is not json")).isNotNull();
        await().atMost(WAIT).until(() -> status("CLM-5202") != null);
        assertThat(status("CLM-5202")).isEqualTo("PAID");
    }

    private void send(ClaimReceived event) throws Exception {
        producer.send(Topics.CLAIMS_RECEIVED, event.memberId(), json.writeValueAsString(event)).get();
    }

    private String status(String claimId) {
        List<String> rows = jdbc.queryForList("SELECT status FROM adjudications WHERE claim_id = ?", String.class,
                claimId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static ClaimReceived officeVisit(String claimId, String memberId) {
        return new ClaimReceived(claimId, memberId, "1234567890", LocalDate.of(2026, 10, 20),
                List.of(new ClaimReceived.Line(1, "99213", new BigDecimal("150.00"))));
    }

    private static ConsumerRecord<String, String> readUntilFound(String topic, String text) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, String>> seen = new ArrayList<>();
            long deadline = System.currentTimeMillis() + WAIT.toMillis();
            while (System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(seen::add);
                var match = seen.stream().filter(r -> r.value().contains(text)).findFirst();
                if (match.isPresent()) {
                    return match.get();
                }
            }
            throw new AssertionError("Nothing containing '" + text + "' on " + topic);
        }
    }
}
