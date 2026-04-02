package com.finflow.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdGeneratorTest {

    @Test
    void generate_returnsValidUuidString() {
        String id = IdGenerator.generate();
        assertNotNull(id);
        UUID.fromString(id);
    }

    @Test
    void correlationId_hasPrefixAndBoundedLength() {
        String corr = IdGenerator.correlationId();
        assertTrue(corr.startsWith("corr-"));
        assertEquals(23, corr.length());
        assertFalse(corr.contains(" "));
    }
}
