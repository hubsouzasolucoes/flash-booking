package com.samuel.flashbooking.application.reservation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Builds the stable identity of the semantic reservation request. */
public final class RequestFingerprint {
    private RequestFingerprint() {}

    public static String reservation(UUID eventId, int quantity) {
        String canonical = "eventId=" + eventId + "\nquantity=" + quantity;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }
}
