-- M5: the Adjudication Service's own tables, in its own schema.

-- One row per decided claim. The PRIMARY KEY makes a redelivered event harmless: the second insert can't happen.
CREATE TABLE adjudications (
    claim_id       VARCHAR(32)    PRIMARY KEY,
    member_id      VARCHAR(32)    NOT NULL,
    status         VARCHAR(16)    NOT NULL,
    reason_code    VARCHAR(64)    NOT NULL,
    message        TEXT           NOT NULL,
    allowed        NUMERIC(12, 2),
    plan_pays      NUMERIC(12, 2),
    member_pays    NUMERIC(12, 2),
    adjudicated_at TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- What the duplicate and visit-limit rules look back at (the engine's ClaimHistory port).
CREATE TABLE claim_history (
    claim_id        VARCHAR(32) PRIMARY KEY,
    member_id       VARCHAR(32) NOT NULL,
    provider_npi    CHAR(10)    NOT NULL,
    date_of_service DATE        NOT NULL,
    cpt_codes       TEXT        NOT NULL,       -- sorted, comma-separated: "36415,99213"
    status          VARCHAR(16) NOT NULL
);
CREATE INDEX claim_history_provider_day ON claim_history (provider_npi, date_of_service);

-- Same outbox shape as the Intake Service (event-outbox library).
CREATE TABLE outbox (
    id           BIGSERIAL    PRIMARY KEY,
    topic        VARCHAR(128) NOT NULL,
    msg_key      VARCHAR(128) NOT NULL,
    event_type   VARCHAR(64)  NOT NULL,
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX outbox_unpublished ON outbox (id) WHERE published_at IS NULL;
