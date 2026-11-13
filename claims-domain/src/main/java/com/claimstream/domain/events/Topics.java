package com.claimstream.domain.events;

/** Kafka topic names: the contract between services. Renaming one is a breaking change for every consumer. */
public final class Topics {

    /** Intake publishes one event per accepted claim. Key = member id, so one member's claims stay in order. */
    public static final String CLAIMS_RECEIVED = "claims.received";

    /** Adjudication publishes the verdict. Copilot, payments and remittance (later milestones) read it. */
    public static final String CLAIMS_ADJUDICATED = "claims.adjudicated";

    /** Where a message goes after retries fail, so one bad message can't block the partition behind it. */
    public static final String CLAIMS_RECEIVED_DLT = "claims.received.DLT";

    private Topics() {
    }
}
