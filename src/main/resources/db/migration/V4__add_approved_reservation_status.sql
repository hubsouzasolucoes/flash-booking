ALTER TABLE reservations DROP CONSTRAINT ck_reservations_status;
ALTER TABLE reservations
    ADD CONSTRAINT ck_reservations_status CHECK (status IN ('PENDING', 'APPROVED', 'CANCELLED', 'EXPIRED'));
