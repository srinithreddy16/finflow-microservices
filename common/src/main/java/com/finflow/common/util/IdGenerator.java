package com.finflow.common.util;

import java.util.UUID;

/**
 * Generates identifiers for entities and correlation keys.
 */
public final class IdGenerator {

    private IdGenerator() {}

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static String correlationId() {
        String uuid = UUID.randomUUID().toString();
        return "corr-" + uuid.substring(0, Math.min(18, uuid.length()));
    }
}
