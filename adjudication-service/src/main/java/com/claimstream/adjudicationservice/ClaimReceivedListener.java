package com.claimstream.adjudicationservice;

import com.claimstream.domain.events.ClaimReceived;
import com.claimstream.domain.events.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The Kafka side: turn a message into a {@link ClaimReceived} and hand it on. Kept thin on purpose, so the
 * business logic in {@link ClaimAdjudicator} can be tested without Kafka.
 *
 * <p>The offset is committed only after this method returns. If it throws, the error handler retries and then
 * parks the message on the dead-letter topic (see {@link KafkaErrorHandling}).
 */
@Component
public class ClaimReceivedListener {

    private final ClaimAdjudicator adjudicator;
    private final ObjectMapper json;

    public ClaimReceivedListener(ClaimAdjudicator adjudicator, ObjectMapper json) {
        this.adjudicator = adjudicator;
        this.json = json;
    }

    @KafkaListener(topics = Topics.CLAIMS_RECEIVED)
    public void onClaimReceived(String payload) throws JsonProcessingException {
        adjudicator.handle(json.readValue(payload, ClaimReceived.class));
    }
}
