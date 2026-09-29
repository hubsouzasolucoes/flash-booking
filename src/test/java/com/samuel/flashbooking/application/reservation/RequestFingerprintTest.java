package com.samuel.flashbooking.application.reservation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFingerprintTest {
    @Test
    void isStableAndIncludesEverySemanticField() {
        UUID event = UUID.randomUUID();
        assertThat(RequestFingerprint.reservation(event, 2))
                .hasSize(64)
                .isEqualTo(RequestFingerprint.reservation(event, 2))
                .isNotEqualTo(RequestFingerprint.reservation(event, 3))
                .isNotEqualTo(RequestFingerprint.reservation(UUID.randomUUID(), 2));
    }
}
