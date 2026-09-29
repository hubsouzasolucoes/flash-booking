package com.samuel.flashbooking.application;

import java.util.UUID;

public final class CorrelationIds {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private CorrelationIds() {
    }

    public static UUID currentOrNew() {
        return CURRENT.get() == null ? UUID.randomUUID() : CURRENT.get();
    }

    public static void set(UUID id) {
        CURRENT.set(id);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
