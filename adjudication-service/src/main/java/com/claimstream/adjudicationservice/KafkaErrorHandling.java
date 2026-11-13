package com.claimstream.adjudicationservice;

import com.claimstream.domain.events.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * What to do when a message can't be processed.
 *
 * <ul>
 *   <li>Temporary failure (DB blip): retry twice, 500 ms apart.</li>
 *   <li>Still failing, or a message that can never work (bad JSON, invalid claim): publish it to
 *       {@code claims.received.DLT} and move on. A "poison pill" must not block every claim behind it.</li>
 * </ul>
 * Spring Boot plugs this bean into every {@code @KafkaListener} automatically.
 */
@Configuration
public class KafkaErrorHandling {

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafka) {
        var toDeadLetters = new DeadLetterPublishingRecoverer(kafka,
                (record, error) -> new TopicPartition(Topics.CLAIMS_RECEIVED_DLT, -1)); // -1: let Kafka pick
        var handler = new DefaultErrorHandler(toDeadLetters, new FixedBackOff(500, 2));
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class,
                NullPointerException.class);
        return handler;
    }
}
