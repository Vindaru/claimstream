package com.claimstream.adjudicationservice;

import com.claimstream.adjudication.AdjudicationEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the plain-Java M2 engine to this service's adapters (ports and adapters / hexagonal architecture). */
@Configuration
public class EngineConfiguration {

    @Bean
    AdjudicationEngine adjudicationEngine(ReferenceData reference, JdbcClaimHistory history) {
        return AdjudicationEngine.withStandardRules(reference, reference, reference, history);
    }
}
