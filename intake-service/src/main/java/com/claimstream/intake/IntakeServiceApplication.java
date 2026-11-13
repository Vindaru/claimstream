package com.claimstream.intake;

import com.claimstream.domain.events.Topics;
import com.claimstream.outbox.OutboxConfiguration;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;

/** Entry point for the Intake Service, the front door every claim enters through. */
@SpringBootApplication
@Import(OutboxConfiguration.class)
public class IntakeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntakeServiceApplication.class, args);
    }

    /**
     * The producer owns its topic. 6 partitions = up to 6 adjudication consumers working in parallel.
     * Spring's KafkaAdmin creates it at startup if it doesn't exist yet.
     */
    @Bean
    NewTopic claimsReceived() {
        return TopicBuilder.name(Topics.CLAIMS_RECEIVED).partitions(6).replicas(1).build();
    }
}
