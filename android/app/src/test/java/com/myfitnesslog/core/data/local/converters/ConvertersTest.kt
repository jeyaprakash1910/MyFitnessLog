package com.myfitnesslog.core.data.local.converters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * Pure JVM unit tests for the Room TypeConverters. They compile and run without
 * an entity or a database instance, verifying round-trip fidelity now (Phase 1)
 * so the converters are trusted when the database is declared in Phase 2.
 */
class ConvertersTest {

    private val uuidConverter = UuidConverter()
    private val instantConverter = InstantConverter()

    @Test
    fun shouldRoundTripUuid() {
        val uuid = UUID.randomUUID()
        val stored = uuidConverter.fromUuid(uuid)
        assertEquals(uuid, uuidConverter.toUuid(stored))
    }

    @Test
    fun shouldReturnNullForNullUuid() {
        assertNull(uuidConverter.fromUuid(null))
        assertNull(uuidConverter.toUuid(null))
    }

    @Test
    fun shouldRoundTripInstantAtMillisecondPrecision() {
        val instant = Instant.ofEpochMilli(1_752_990_600_123L)
        val stored = instantConverter.fromInstant(instant)
        assertEquals(instant, instantConverter.toInstant(stored))
    }

    @Test
    fun shouldReturnNullForNullInstant() {
        assertNull(instantConverter.fromInstant(null))
        assertNull(instantConverter.toInstant(null))
    }
}
