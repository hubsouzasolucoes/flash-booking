CREATE TABLE events
(
    id                UUID PRIMARY KEY,
    name              VARCHAR(160) NOT NULL,
    starts_at         TIMESTAMPTZ  NOT NULL,
    capacity          INTEGER      NOT NULL CHECK (capacity > 0),
    available_tickets INTEGER      NOT NULL CHECK (available_tickets >= 0),
    created_at        TIMESTAMPTZ  NOT NULL
);
CREATE TABLE reservations
(
    id         UUID PRIMARY KEY,
    event_id   UUID        NOT NULL REFERENCES events (id),
    quantity   INTEGER     NOT NULL CHECK (quantity > 0),
    status     VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_reservations_expiration ON reservations (status, expires_at);
CREATE TABLE idempotency_records
(
    id              UUID PRIMARY KEY,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    request_hash    VARCHAR(64)  NOT NULL,
    reservation_id  UUID         NOT NULL REFERENCES reservations (id),
    created_at      TIMESTAMPTZ  NOT NULL
);
CREATE TABLE outbox_events
(
    id             UUID PRIMARY KEY,
    aggregate_id   UUID         NOT NULL,
    aggregate_type VARCHAR(80)  NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ NULL
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (occurred_at) WHERE published_at IS NULL;
