package com.claimstream.adjudicationservice;

import com.claimstream.domain.events.Topics;
import com.claimstream.outbox.OutboxConfiguration;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;

/** Reads claims.received, decides each claim with the M2 engine, announces the verdict on claims.adjudicated. */
@SpringBootApplication
@Import(OutboxConfiguration.class)
public class AdjudicationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdjudicationServiceApplication.class, args);
    }

    @Bean
    NewTopic claimsAdjudicated() {
        return TopicBuilder.name(Topics.CLAIMS_ADJUDICATED).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic claimsReceivedDeadLetters() {
        return TopicBuilder.name(Topics.CLAIMS_RECEIVED_DLT).partitions(1).replicas(1).build();
    }
}
