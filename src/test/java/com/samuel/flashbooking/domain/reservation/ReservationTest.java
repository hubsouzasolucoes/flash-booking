package com.samuel.flashbooking.domain.reservation;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ReservationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void cancellationIsASinglePendingTransition() {
        var reservation = Reservation.create(UUID.randomUUID(), 2, NOW.plusSeconds(60), NOW);
        assertThat(reservation.cancel(NOW.plusSeconds(1))).isTrue();
        assertThat(reservation.cancel(NOW.plusSeconds(2))).isFalse();
        assertThat(reservation.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.updatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void onlyExpiredPendingReservationsTransition() {
        var reservation = Reservation.create(UUID.randomUUID(), 1, NOW.plusSeconds(60), NOW);
        assertThat(reservation.expire(NOW.plusSeconds(59))).isFalse();
        assertThat(reservation.expire(NOW.plusSeconds(60))).isTrue();
        assertThat(reservation.expire(NOW.plusSeconds(61))).isFalse();
        assertThat(reservation.status()).isEqualTo(ReservationStatus.EXPIRED);
    }
}
