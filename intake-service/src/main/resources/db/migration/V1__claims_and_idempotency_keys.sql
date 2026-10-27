-- M4: claims received through the REST API, and the idempotency keys that protect them.

-- The PRIMARY KEY on claim_id is the business guarantee: one row per claim, however many times it is sent.
CREATE TABLE claims (
    claim_id        VARCHAR(32)    PRIMARY KEY,
    member_id       VARCHAR(32)    NOT NULL,
    provider_npi    CHAR(10)       NOT NULL,
    date_of_service DATE           NOT NULL,
    total_billed    NUMERIC(12, 2) NOT NULL,
    status          VARCHAR(16)    NOT NULL,
    lines           JSONB          NOT NULL,
    received_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- One row per Idempotency-Key: what was asked (request_hash) and what we answered, so a retry gets the same answer.
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_hash    CHAR(64)     NOT NULL,
    claim_id        VARCHAR(32)  NOT NULL REFERENCES claims (claim_id),
    response_status INT          NOT NULL,
    response_body   JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
