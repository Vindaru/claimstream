package com.claimstream.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Add {@code @Import(OutboxConfiguration.class)} to a service to get the writer and the relay.
 * The service owns the {@code outbox} table through its own Flyway migration.
 */
@Configuration
@EnableScheduling
public class OutboxConfiguration {

    @Bean
    public OutboxWriter outboxWriter(JdbcTemplate jdbc, ObjectMapper json) {
        return new OutboxWriter(jdbc, json);
    }

    /** Turn off with {@code claimstream.outbox.relay.enabled=false}, e.g. to watch rows pile up in a lesson. */
    @Bean
    @ConditionalOnProperty(name = "claimstream.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
    public OutboxRelay outboxRelay(JdbcTemplate jdbc, TransactionTemplate tx, KafkaTemplate<String, String> kafka,
                                   @Value("${claimstream.outbox.batch-size:500}") int batchSize) {
        return new OutboxRelay(jdbc, tx, kafka, batchSize);
    }
}
