ALTER TABLE events
    ADD CONSTRAINT ck_events_available_not_above_capacity CHECK (available_tickets <= capacity);

ALTER TABLE reservations
    ADD CONSTRAINT ck_reservations_status CHECK (status IN ('PENDING', 'CANCELLED', 'EXPIRED'));
