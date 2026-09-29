ALTER TABLE events ADD COLUMN version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0);

ALTER TABLE outbox_events ADD COLUMN event_version INTEGER NOT NULL DEFAULT 1 CHECK (event_version > 0);
ALTER TABLE outbox_events ADD COLUMN aggregate_version BIGINT NOT NULL DEFAULT 1 CHECK (aggregate_version > 0);
ALTER TABLE outbox_events ADD CONSTRAINT uq_outbox_aggregate_version UNIQUE (aggregate_id, aggregate_version);
ALTER TABLE outbox_events ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE outbox_events ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0);
ALTER TABLE outbox_events ADD COLUMN next_attempt_at TIMESTAMPTZ;
ALTER TABLE outbox_events ADD COLUMN locked_until TIMESTAMPTZ;
ALTER TABLE outbox_events ADD COLUMN last_error VARCHAR(500);
UPDATE outbox_events SET next_attempt_at = occurred_at;
ALTER TABLE outbox_events ALTER COLUMN next_attempt_at SET NOT NULL;
ALTER TABLE outbox_events ADD CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED'));
UPDATE outbox_events SET status = 'PUBLISHED' WHERE published_at IS NOT NULL;
DROP INDEX idx_outbox_unpublished;
CREATE INDEX idx_outbox_poll ON outbox_events (status, next_attempt_at, occurred_at)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE TABLE inbox_events (
    event_id UUID NOT NULL,
    consumer VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, consumer)
);
CREATE INDEX idx_inbox_processed_at ON inbox_events (processed_at);

CREATE TABLE event_availability_projection (
    event_id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    total_capacity INTEGER NOT NULL CHECK (total_capacity > 0),
    available_tickets INTEGER NOT NULL CHECK (available_tickets >= 0 AND available_tickets <= total_capacity),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_event_version BIGINT NOT NULL CHECK (last_event_version > 0)
);
