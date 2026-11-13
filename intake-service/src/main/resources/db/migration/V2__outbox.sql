-- M5: transactional outbox. A row is written in the same transaction as the claim, then relayed to Kafka.
CREATE TABLE outbox (
    id           BIGSERIAL    PRIMARY KEY,           -- relay order
    topic        VARCHAR(128) NOT NULL,
    msg_key      VARCHAR(128) NOT NULL,              -- Kafka key: member id, so one member's claims stay in order
    event_type   VARCHAR(64)  NOT NULL,
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ                          -- NULL = not sent yet
);

-- The relay only ever looks for unsent rows; a partial index keeps that lookup small as the table grows.
CREATE INDEX outbox_unpublished ON outbox (id) WHERE published_at IS NULL;
